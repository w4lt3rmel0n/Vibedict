/*
 * Copyright (c) 2025-Present
 * All rights reserved.
 *
 * This code is licensed under the BSD 3-Clause License.
 * See the LICENSE file for details.
 */

#include "include/mdict.h"

#include <encode/api.h>
#include <encode/base64.h>

#include <algorithm>
#include <cstring>
#include <filesystem>
#include <iostream>
#include <map>
#include <regex>
#include <stdexcept>
#include <utility>
#include <cctype>
#include <android/log.h>

#include "encode/char_decoder.h"
#include "encode/api.h"
#include "include/adler32.h"
#include "include/binutils.h"
#include "include/mdict_extern.h"
#include "include/xmlutils.h"
#include "include/zlib_wrapper.h"

#define LOG_TAG "MdictJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

const std::regex re_pattern("(\\s|:|\\.|,|-|_|'|\\(|\\)|#|<|>|!)");

namespace mdict {

// constructor
    Mdict::Mdict(std::string fn) noexcept : filename(std::move(fn)) {
        if (endsWith(filename, ".mdd")) {
            this->filetype = MDDTYPE;
        } else {
            this->filetype = MDXTYPE;
        }
    }

// constructor accepting file descriptor (zero-copy on Android)
    Mdict::Mdict(int fd, bool is_mdd) noexcept : filename("") {
        // Default to MDXTYPE for FD-based dictionaries
        // If you need MDD detection, consider passing it as a parameter
        if (is_mdd) {
            this->filetype = MDDTYPE;
        } else {
            this->filetype = MDXTYPE;
        }

        // Associate the file descriptor with a FILE* stream
        // fdopen takes ownership of the FD
        this->file_ptr = fdopen(fd, "rb");
    }

// distructor
    Mdict::~Mdict() {
        // Close the file pointer (also closes the underlying FD if opened via fdopen)
        if (this->file_ptr) {
            fclose(this->file_ptr);
            this->file_ptr = nullptr;
        }
    }

/**
 * transform word into comparable string
 * @param word
 * @return
 */

    std::string normalize_path(const std::string& path) {
        std::string res = path;
        // 1. Lowercase
        std::transform(res.begin(), res.end(), res.begin(), ::tolower);

        // 2. Uniform separators to backslash '\' (standard for MDict)
        std::replace(res.begin(), res.end(), '/', '\\');

        // 3. Ensure leading backslash
        if (!res.empty() && res[0] != '\\') {
            res.insert(0, "\\");
        }
        return res;
    }

    std::string Mdict::extract_body_content(const std::string& html) {
        // Find "<body" or "<BODY"
        size_t body_start = html.find("<body");
        if (body_start == std::string::npos) {
            body_start = html.find("<BODY");
        }

        if (body_start == std::string::npos) {
            LOGD("extract_body_content: No <body tag found, returning original string (fragment?).");
            return html; // Fallback: no <body> tag, assume it's a fragment
        }

        // Find the closing '>' of the <body ...> tag
        size_t tag_end = html.find('>', body_start + 1);
        if (tag_end == std::string::npos) {
            LOGD("extract_body_content: Found '<body' but no closing '>', returning original.");
            return html; // Malformed tag
        }

        // The content *starts* after the '>'
        body_start = tag_end + 1;

        // Find "</body>" or "</BODY>"
        size_t body_end = html.rfind("</body>");
        if (body_end == std::string::npos) {
            body_end = html.rfind("</BODY>");
        }

        if (body_end == std::string::npos || body_end <= body_start) {
            LOGD("extract_body_content: No </body> tag found, returning original string.");
            return html; // Fallback: no </body> tag found
        }

        LOGD("extract_body_content: Extracted body from %zu to %zu", body_start, body_end);
        return html.substr(body_start, body_end - body_start);
    }

/**
 * transform word into comparable string
 * @param word
 * @return
 */
    std::string _s(std::string word) {
        // This function creates a "stripped" version of a key for comparison.
        // It must be case-insensitive and ignore common punctuation.

        std::string s;
        s.reserve(word.length());

        for (const unsigned char c : word) {
            // 1. Convert ASCII to lowercase
            if (c >= 'A' && c <= 'Z') {
                s += (c + 32);
            }
                // 2. Ignore common ASCII punctuation/symbols
            else if (c == ' ' || c == ':' || c == '.' || c == ',' || c == '-' ||
                     c == '_' || c == '\'' || c == '(' || c == ')' || c == '#' ||
                     c == '<' || c == '>' || c == '!' || c == '/' || c == '\\' ||
                     c == '[' || c == ']' || c == '{' || c == '}' || c == '@' ) // Also ignore Japanese brackets
            {
                // Do nothing, skip the character
            }
                // 3. Keep all other characters (including numbers, Japanese, etc.)
            else {
                s += c;
            }
        }

        // Note: We avoid std::transform(::tolower) because it is
        // locale-dependent and can break UTF-8 strings.
        // This manual method is safer for this app.

        return s;
    }

    int32_t Mdict::get_match_count(const std::string& key)
    {
        // Find the first matching key
        // NOTE: We must compare item->key_word (a string) with key (a string)
        auto it = std::lower_bound(key_list.begin(), key_list.end(), key,
                                   [](const key_list_item* item, const std::string& k) {
                                       return item->key_word < k;
                                   });

        int32_t count = 0;
        if (it == key_list.end() || (*it)->key_word != key) {
            // Key not found
            return 0;
        }

        // Key was found, now count all adjacent identical keys
        while (it != key_list.end() && (*it)->key_word == key) {
            count++;
            it++;
        }

        return count;
    }

/***************************************
 * private part            *
 ***************************************/

/**
 * read header
 */
    void Mdict::read_header() {
        // -----------------------------------------
        // 1. [0:4] dictionary header length 4 byte
        // -----------------------------------------

        // header size buffer
        char *head_size_buf = (char *)std::calloc(4, sizeof(char));
        readfile(0, 4, head_size_buf);

        // header byte size convert
        uint32_t header_bytes_size =
                be_bin_to_u32((const unsigned char *)head_size_buf);
        free(head_size_buf);
        // assign key block start offset
        this->header_bytes_size = header_bytes_size;
        this->key_block_start_offset = this->header_bytes_size + 8;
        /// passed

        // -----------------------------------------
        // 2. [4: header_bytes_size+4], header buffer
        // -----------------------------------------

        // header buffer
        unsigned char *head_buffer =
                (unsigned char *)std::calloc(header_bytes_size, sizeof(unsigned char));
        readfile(4, header_bytes_size, (char *)head_buffer);
        /// passed

        // 3. alder32 checksum
        // -----------------------------------------

        // TODO  version < 2.0 needs to checksum?
        // alder32 checksum buffer
        char *head_checksum_buffer = (char *)std::calloc(4, sizeof(char));
        readfile(header_bytes_size + 4, 4, head_checksum_buffer);
        /// passed

        // TODO skip head checksum for now
        free(head_checksum_buffer);

        // -----------------------------------------
        // 4. convert header buffer into utf16 text
        // -----------------------------------------

        // header text utf16

        std::string utf8_temp;
        if (!utf16_to_utf8_header(head_buffer, header_bytes_size, utf8_temp)) {
            std::cout << "this mdx file is invalid len:" << header_bytes_size << std::endl;
            return;
        }

        unsigned char* utf8_buffer = reinterpret_cast<unsigned char*>(&utf8_temp[0]);
        int utf8_len = static_cast<int>(utf8_temp.size());

        this->header_buffer = std::move(utf8_temp);

        std::string header_text(reinterpret_cast<char*>(utf8_buffer), utf8_len);
        std::map<std::string, std::string> headinfo;
        parse_xml_header(header_text, headinfo);
        /// passed

        // -----------------------------------------
        // 6. handle header message, set flags
        // -----------------------------------------

        // encrypted flag
        // 0x00 - no encryption
        // 0x01 - encrypt record block
        // 0x02 - encrypt key info block
        if (headinfo.find("Encrypted") == headinfo.end() ||
            headinfo["Encrypted"].empty() || headinfo["Encrypted"] == "No") {
            this->encrypt = ENCRYPT_NO_ENC;
        } else if (headinfo["Encrypted"] == "Yes") {
            this->encrypt = ENCRYPT_RECORD_ENC;
        } else {
            std::string s = headinfo["Encrypted"];
            if (s.at(0) == '2') {
                this->encrypt = ENCRYPT_KEY_INFO_ENC;
            } else if (s.at(0) == '1') {
                this->encrypt = ENCRYPT_RECORD_ENC;
            } else {
                this->encrypt = ENCRYPT_NO_ENC;
            }
        }
        /// passed

        // -------- stylesheet ----------
        // stylesheet attribute if present takes from of:
        // style_number # 1-255
        // style_begin # or ''
        // style_end # or ''
        // TODO: splitstyle info

        // header_info['_stylesheet'] = {}
        // if header_tag.get('StyleSheet'):
        //   lines = header_tag['StyleSheet'].splitlines()
        //   for i in range(0, len(lines), 3):
        //        header_info['_stylesheet'][lines[i]] = (lines[i + 1], lines[i + 2])

        // ---------- version ------------
        // before version 2.0, number is 4 bytes integer
        // version 2.0 and above use 8 bytes
        std::string sver = headinfo["GeneratedByEngineVersion"];
        std::string::size_type sz; // alias of size_t

        auto parse_version = [](const std::string& s, float fallback = 0.0f) -> float {
            float v = fallback;
            size_t i = 0;

            // skip leading whitespace
            while (i < s.size() && std::isspace(static_cast<unsigned char>(s[i]))) ++i;
            if (i == s.size()) return fallback;

            // parse digits before decimal
            float int_part = 0;
            while (i < s.size() && std::isdigit(static_cast<unsigned char>(s[i]))) {
                int_part = int_part * 10 + (s[i] - '0');
                ++i;
            }

            float frac_part = 0;
            if (i < s.size() && s[i] == '.') {
                ++i;
                float divisor = 10.0f;
                while (i < s.size() && std::isdigit(static_cast<unsigned char>(s[i]))) {
                    frac_part += (s[i] - '0') / divisor;
                    divisor *= 10.0f;
                    ++i;
                }
            }

            v = int_part + frac_part;
            return v;
        };

        // we fallback to less than 2.
        this->version = parse_version(sver, 0.0f); // default < 2.0


        if (this->version >= 2.0) {
            this->number_width = 8;
            this->number_format = NUMFMT_BE_8BYTESQ;
            this->key_block_info_start_offset = this->key_block_start_offset + 40 + 4;
        } else {
            this->number_format = NUMFMT_BE_4BYTESI;
            this->number_width = 4;
            this->key_block_info_start_offset = this->key_block_start_offset + 16;
        }

        // ---------- encoding ------------
        if (headinfo.find("Encoding") != headinfo.end() ||
            headinfo["Encoding"] == "" || headinfo["Encoding"] == "UTF-8") {
            this->encoding = ENCODING_UTF8;
        } else if (headinfo["Encoding"] == "GBK" ||
                   headinfo["Encoding"] == "GB2312") {
            this->encoding = ENCODING_GB18030;
        } else if (headinfo["Encoding"] == "Big5" || headinfo["Encoding"] == "BIG5") {
            this->encoding = ENCODING_BIG5;
        } else if (headinfo["Encoding"] == "utf16" ||
                   headinfo["Encoding"] == "utf-16") {
            this->encoding = ENCODING_UTF16;
        } else {
            this->encoding = ENCODING_UTF8;
        }
        // FIX mdd
        if (this->filetype == "MDD") {
            this->encoding = ENCODING_UTF16;
        }
        /// passed
    }

/**
 * read key block header, key block header contains a serials number, including
 *
 * key block header info struct:
 * [0:8]/[0:4]   - number of key blocks
 * [8:16]/[4:8]  - number of entries
 * [16:24]/nil - key block info decompressed size (if version >= 2.0,
 * otherwise, this section does not exist)
 * [24:32]/[8:12] - key block info size
 * [32:40][12:16] - key block size
 * note: if version <2.0, the key info buffer size is 4 * 4
 * otherwise, ths key info buffer size is 5 * 8
 * <2.0  the order of number is same
 */
    void Mdict::read_key_block_header() {
        // key block header part
        int key_block_info_bytes_num = 0;
        if (this->version >= 2.0) {
            key_block_info_bytes_num = 8 * 5;
        } else {
            key_block_info_bytes_num = 4 * 4;
        }

        // key block info buffer
        char *key_block_info_buffer = (char *)calloc(
                static_cast<size_t>(key_block_info_bytes_num), sizeof(char));
        // read buffer
        this->readfile(this->key_block_start_offset,
                       static_cast<uint64_t>(key_block_info_bytes_num),
                       key_block_info_buffer);
        //  putbytes(key_block_info_buffer,key_block_info_bytes_num, true);
        /// PASSED

        // TODO key block info encrypted file not support yet
        if (this->encrypt == ENCRYPT_RECORD_ENC) {
            std::cout << "user identification is needed to read encrypted file"
                      << std::endl;
            if (key_block_info_buffer)
                std::free(key_block_info_buffer);
            throw std::invalid_argument("invalid encrypted file");
        }

        // key block header info struct:
        // [0:8]/[0:4]   - number of key blocks
        // [8:16]/[4:8]  - number of entries
        // [16:24]/nil - key block info decompressed size (if version >= 2.0,
        // otherwise, this section does not exist)
        // [24:32]/[8:12] - key block info size
        // [32:40][12:16] - key block size
        // note: if version <2.0, the key info buffer size is 4 * 4
        //       otherwise, ths key info buffer size is 5 * 8
        // <2.0  the order of number is same

        // 1. [0:8]([0:4]) number of key blocks
        char *key_block_nums_bytes =
                (char *)calloc(static_cast<size_t>(this->number_width), sizeof(char));
        int eno = bin_slice(key_block_info_buffer, key_block_info_bytes_num, 0,
                            this->number_width, key_block_nums_bytes);
        if (eno != 0) {
            if (key_block_info_buffer)
                std::free(key_block_info_buffer);
            if (key_block_nums_bytes)
                std::free(key_block_nums_bytes);
            std::cout << "eno: " << eno << std::endl;
            throw std::logic_error("get key block bin slice failed");
        }
        /// passed

        uint64_t key_block_num = 0;
        if (this->number_width == 8)
            key_block_num = be_bin_to_u64((const unsigned char *)key_block_nums_bytes);
        else if (this->number_width == 4)
            key_block_num = be_bin_to_u32((const unsigned char *)key_block_nums_bytes);
        if (key_block_nums_bytes)
            std::free(key_block_nums_bytes);
        /// passed

        // 2. [8:16]  - number of entries
        char *entries_num_bytes =
                (char *)calloc(static_cast<size_t>(this->number_width), sizeof(char));
        eno = bin_slice(key_block_info_buffer, key_block_info_bytes_num,
                        this->number_width, this->number_width, entries_num_bytes);
        if (eno != 0) {
            if (key_block_info_buffer)
                std::free(key_block_info_buffer);
            if (entries_num_bytes)
                std::free(entries_num_bytes);
            throw std::logic_error("get key block bin slice failed");
        }
        /// passed

        uint64_t entries_num = 0;
        if (this->number_width == 8)
            entries_num = be_bin_to_u64((const unsigned char *)entries_num_bytes);
        else if (this->number_width == 4)
            key_block_num = be_bin_to_u32((const unsigned char *)entries_num_bytes);
        if (entries_num_bytes)
            std::free(entries_num_bytes);
        /// passed

        int key_block_info_size_start_offset = 0;

        // 3. [16:24] - key block info decompressed size (if version >= 2.0,
        // otherwise, this section does not exist)
        if (this->version >= 2.0) {
            char *key_block_info_decompress_size_bytes =
                    (char *)calloc(static_cast<size_t>(this->number_width), sizeof(char));
            eno = bin_slice(key_block_info_buffer, key_block_info_bytes_num,
                            this->number_width * 2, this->number_width,
                            key_block_info_decompress_size_bytes);
            if (eno != 0) {
                if (key_block_info_buffer)
                    std::free(key_block_info_buffer);
                if (key_block_info_decompress_size_bytes)
                    std::free(key_block_info_decompress_size_bytes);
                throw std::logic_error("decode key block decompress size failed");
            }
            /// passed

            uint64_t key_block_info_decompress_size = 0;
            if (this->number_width == 8)
                key_block_info_decompress_size = be_bin_to_u64(
                        (const unsigned char *)key_block_info_decompress_size_bytes);
            else if (this->number_width == 4)
                key_block_info_decompress_size = be_bin_to_u32(
                        (const unsigned char *)key_block_info_decompress_size_bytes);
            this->key_block_info_decompress_size = key_block_info_decompress_size;
            if (key_block_info_decompress_size_bytes)
                std::free(key_block_info_decompress_size_bytes);
            /// passed

            // key block info size (number) start at 24 ([24:32])
            key_block_info_size_start_offset = this->number_width * 3;
        } else {
            // key block info size (number) start at 24 ([8:12])
            key_block_info_size_start_offset = this->number_width * 2;
        }

        // 4. [24:32] - key block info size
        char *key_block_info_size_buffer =
                (char *)calloc(static_cast<size_t>(this->number_width), sizeof(char));
        eno = bin_slice(key_block_info_buffer, key_block_info_bytes_num,
                        key_block_info_size_start_offset, this->number_width,
                        key_block_info_size_buffer);
        if (eno != 0) {
            if (key_block_info_buffer != nullptr)
                std::free(key_block_info_buffer);
            if (key_block_info_size_buffer != nullptr)
                std::free(key_block_info_size_buffer);
            throw std::logic_error("decode key block info size failed");
        }

        uint64_t key_block_info_size = 0;
        if (this->number_width == 8)
            key_block_info_size =
                    be_bin_to_u64((const unsigned char *)key_block_info_size_buffer);
        else if (this->number_width == 4)
            key_block_info_size =
                    be_bin_to_u32((const unsigned char *)key_block_info_size_buffer);
        if (key_block_info_size_buffer != nullptr)
            std::free(key_block_info_size_buffer);
        /// passed

        // 5. [32:40] - key block size
        char *key_block_size_buffer =
                (char *)calloc(static_cast<size_t>(this->number_width), sizeof(char));
        eno = bin_slice(key_block_info_buffer, key_block_info_bytes_num,
                        key_block_info_size_start_offset + this->number_width,
                        this->number_width, key_block_size_buffer);
        if (eno != 0) {
            if (key_block_info_buffer)
                std::free(key_block_info_buffer);
            if (key_block_size_buffer)
                std::free(key_block_size_buffer);
            throw std::logic_error("decode key block size failed");
        }
        /// passed

        uint64_t key_block_size = 0;
        if (this->number_width == 8)
            key_block_size =
                    be_bin_to_u64((const unsigned char *)key_block_size_buffer);
        else if (this->number_width == 4)
            key_block_size =
                    be_bin_to_u32((const unsigned char *)key_block_size_buffer);
        if (key_block_size_buffer)
            std::free(key_block_size_buffer);
        /// passed

        // 6. [40:44] - 4bytes checksum
        // TODO if version > 2.0, skip 4bytes checksum

        // free key block info buffer
        if (key_block_info_buffer != nullptr)
            std::free(key_block_info_buffer);

        this->key_block_num = key_block_num;
        this->entries_num = entries_num;
        this->key_block_info_size = key_block_info_size;
        this->key_block_size = key_block_size;
        if (this->version >= 2.0) {
            this->key_block_info_start_offset = this->key_block_start_offset + 40 + 4;
        } else {
            this->key_block_info_start_offset = this->key_block_start_offset + 16;
        }
    }

/**
 * read key block info
 *
 * it will decode the key block info, and set the key block info list
 * it contains:
 * first key
 * last key
 * comp size
 * decomp size
 * offset
 */
    void Mdict::read_key_block_info() {
        // start at this->key_block_info_start_offset
        char *key_block_info_buffer = (char *)calloc(
                static_cast<size_t>(this->key_block_info_size), sizeof(char));

        readfile(this->key_block_info_start_offset, this->key_block_info_size,
                 key_block_info_buffer);

        // ------------------------------------
        // decode key_block_info
        // ------------------------------------
        decode_key_block_info(key_block_info_buffer, this->key_block_info_size,
                              this->key_block_num, this->entries_num);

        // key block compressed start offset = this->key_block_info_start_offset +
        // key_block_info_size
        this->key_block_compressed_start_offset = static_cast<uint32_t>(
                this->key_block_info_start_offset + this->key_block_info_size);

        /// passed

        char *key_block_compressed_buffer =
                (char *)calloc(static_cast<size_t>(this->key_block_size), sizeof(char));

        readfile(this->key_block_compressed_start_offset,
                 static_cast<int>(this->key_block_size), key_block_compressed_buffer);

        // ------------------------------------
        // decode key_block_compressed
        // ------------------------------------
        unsigned long kb_len = this->key_block_size;
        //  putbytes(key_block_compressed_buffer,this->key_block_size, true);

        int err =
                decode_key_block((unsigned char *)key_block_compressed_buffer, kb_len);
        if (err != 0) {
            throw std::runtime_error("decode key block error");
        }

        if (key_block_info_buffer != nullptr)
            std::free(key_block_info_buffer);
        if (key_block_compressed_buffer != nullptr)
            std::free(key_block_compressed_buffer);
    }

/**
 * use ripemd128 as decrypt key, and decrypt the key info data
 * @param data the data which needs to decrypt
 * @param k the decrypt key
 * @param data_len data length
 * @param key_len key length
 */
    void fast_decrypt(byte *data, const byte *k, int data_len, int key_len) {
        const byte *key = k;
        //      putbytes((char*)data, 16, true);
        byte *b = data;
        byte previous = 0x36;

        for (int i = 0; i < data_len; ++i) {
            byte t = static_cast<byte>(((b[i] >> 4) | (b[i] << 4)) & 0xff);
            t = t ^ previous ^ ((byte)(i & 0xff)) ^ key[i % key_len];
            previous = b[i];
            b[i] = t;
        }
    }

/**
 *
 * decrypt the data, this is a helper function to invoke the fast_decrypt
 * note: don't forget free comp_block !!
 *
 * @param comp_block compressed block buffer
 * @param comp_block_len compressed block buffer size
 * @return the decrypted compressed block
 */
    byte *mdx_decrypt(byte *comp_block, const int comp_block_len) {
        byte *key_buffer = (byte *)calloc(8, sizeof(byte));
        memcpy(key_buffer, comp_block + 4 * sizeof(char), 4 * sizeof(char));
        key_buffer[4] = 0x95; // comp_block[4:8] + [0x95,0x36,0x00,0x00]
        key_buffer[5] = 0x36;

        byte *key = ripemd128bytes(key_buffer, 8);

        fast_decrypt(comp_block + 8 * sizeof(byte), key, comp_block_len - 8,
                     16 /* key length*/);

        // finally
        std::free(key_buffer);
        return comp_block;
        /// passed
    }

/**
 * split key block into key block list
 *
 * this is for key block (not key block info)
 *
 * @param key_block key block buffer
 * @param key_block_len key block length
 */
    std::vector<key_list_item *> Mdict::split_key_block(unsigned char *key_block,
                                                        unsigned long key_block_len,
                                                        unsigned long block_id) {
        // TODO assert checksum
        // uint32_t adlchk = adler32checksum(key_block, key_block_len);
        //  std::cout<<"adler32 chksum: "<<adlchk<<std::endl;
        int key_start_idx = 0;
        int key_end_idx = 0;
        std::vector<key_list_item *> inner_key_list;

        while (key_start_idx < key_block_len) {
            // # the corresponding record's offset in record block
            unsigned long record_start = 0;
            int width = 0;
            if (this->version >= 2.0) {
                record_start = be_bin_to_u64(key_block + key_start_idx);
            } else {
                record_start = be_bin_to_u32(key_block + key_start_idx);
            }

            if (this->encoding == 1 /* utf16 */) {
                width = 2;
            } else {
                width = 1;
            }

            // key text ends with '\x00'
            // version >= 2.0 delimiter == '0x0000'
            // else delimiter == '0x00'  (< 2.0)
            int i = key_start_idx + number_width; // ver > 2.0, move 8, else move 4
            if (i >= key_block_len) {
                throw std::runtime_error("key start idx > key block length");
            }
            while (static_cast<size_t>(i) < key_block_len) {
                if (encoding == 1 /*ENCODING_UTF16*/) {
                    if ((key_block[i] & 0x0f) == 0 &&        /* delimiter = '0000' */
                        ((key_block[i] & 0xf0) >> 4) == 0 && /* delimiter = '0000' */
                        ((key_block[i + 1] & 0x0f) == 0) &&
                        (((key_block[i + 1] & 0xf0) >> 4) == 0)) {
                        key_end_idx = i;
                        break;
                    }
                } else {
                    // var a = key_block[i]
                    // (a >> 4) & 255                     (01011010 >> 4) & 11111111 ->
                    // 00000101 & 11111111 -> 00000101
                    //
                    if ((key_block[i] & 0xf0) >> 4 == 0 && /* delimiter == '0' */
                        (key_block[i] & 0x0f) >> 0 == 0) {
                        key_end_idx = i;
                        break;
                    }
                }

                i += width;
            }
            /// passed

            if (static_cast<size_t>(key_end_idx) >= key_block_len) {
                key_end_idx = static_cast<int>(key_block_len);
            }

            std::string key_text = "";
            if (this->encoding == 1 /* ENCODING_UTF16 */) {
                std::string hex_input = be_bin_to_utf16(
                        (const char *)key_block, (key_start_idx + this->number_width),
                        static_cast<unsigned long>(key_end_idx - key_start_idx -
                                                   this->number_width));

                size_t utf16le_buf_size =
                        (hex_input.length() / 2) +
                        1; // Add 1 just in case (though hex_to_bytes checks evenness)
                unsigned char *utf16le_bytes = (unsigned char *)malloc(utf16le_buf_size);
                if (!utf16le_bytes) {
                    perror("Error allocating memory for UTF-16LE buffer");
                    throw std::runtime_error("Error allocating memory for UTF-16LE buffer");
                }

                ssize_t utf16_bytes_written =
                        hex_to_bytes(hex_input.c_str(), utf16le_bytes, utf16le_buf_size);
                if (utf16_bytes_written < 0) {
                    free(utf16le_bytes);
                    throw std::runtime_error("hex_to_bytes failed");
                }

                // UTF-16LE Bytes to UTF-8
                // Allocate buffer for UTF-8 output.
                // Estimate: Max 3 bytes UTF-8 per 1 byte UTF-16LE is generous and safe.
                // (Max is 4 UTF-8 bytes per 4 UTF-16LE bytes for surrogates, which is
                // 1x). (Max is 3 UTF-8 bytes per 2 UTF-16LE bytes for BMP, which
                // is 1.5x). So, 3x the number of *UTF-16 bytes* is very safe. Add 1 for
                // null terminator.
                size_t utf8_buf_size = ((size_t)utf16_bytes_written * 3) + 1;
                unsigned char *utf8_output = (unsigned char *)malloc(utf8_buf_size);
                if (!utf8_output) {
                    perror("Error allocating memory for UTF-8 output buffer");
                    free(utf16le_bytes);
                    throw std::runtime_error(
                            "Error allocating memory for UTF-8 output buffer");
                }

                ssize_t utf8_bytes_written =
                        utf16le_to_utf8(utf16le_bytes, (size_t)utf16_bytes_written,
                                        utf8_output, utf8_buf_size);

                if (utf8_bytes_written < 0) {
                    free(utf16le_bytes);
                    free(utf8_output);
                    throw std::runtime_error("utf16le_to_utf8 failed");
                }

                key_text = std::string(reinterpret_cast<char *>(utf8_output),
                                       utf8_bytes_written);
                free(utf16le_bytes);
                free(utf8_output);

            } else if (this->encoding == 0 /* ENCODING_UTF8 */) {
                key_text = be_bin_to_utf8(
                        (const char *)key_block, (key_start_idx + this->number_width),
                        static_cast<unsigned long>(key_end_idx - key_start_idx -
                                                   this->number_width));
            }
            inner_key_list.push_back(new key_list_item(record_start, key_text));

            key_start_idx = key_end_idx + width;
        }
        return inner_key_list;
    }

/**
 * decode key block info by block id use with reduce function
 * @param block_id key_block id
 * @return return key list item
 */
    std::vector<key_list_item *>
    Mdict::decode_key_block_by_block_id(unsigned long block_id) {
        // ------------------------------------
        // decode key_block_compressed
        // ------------------------------------

        unsigned long idx = block_id;

        unsigned long comp_size = this->key_block_info_list[idx]->key_block_comp_size;
        unsigned long decomp_size =
                this->key_block_info_list[idx]->key_block_decomp_size;
        unsigned long start_ofset =
                this->key_block_info_list[idx]->key_block_comp_accumulator +
                this->key_block_compressed_start_offset;

        char *key_block_buffer =
                (char *)calloc(static_cast<size_t>(comp_size), sizeof(unsigned char));

        readfile(start_ofset, static_cast<int>(comp_size), key_block_buffer);

        // 4 bytes comp type
        char *key_block_comp_type = (char *)calloc(4, sizeof(char));
        memcpy(key_block_comp_type, key_block_buffer, 4 * sizeof(char));
        // 4 bytes adler checksum of decompressed key block
        uint32_t chksum =
                be_bin_to_u32((unsigned char *)key_block_buffer + 4 * sizeof(char));

        unsigned char *key_block = nullptr;
        std::vector<uint8_t> kb_uncompressed; // note: ensure kb_uncompressed not
        // die when out of uncompress scope

        if ((key_block_comp_type[0] & 255) == 0) {
            // none compressed
            key_block = (unsigned char *)(key_block_buffer + 8 * sizeof(char));
        } else if ((key_block_comp_type[0] & 255) == 1) {
            // 01000000
            // TODO lzo decompress

        } else if ((key_block_comp_type[0] & 255) == 2) {
            // zlib compress
            kb_uncompressed =
                    zlib_mem_uncompress(key_block_buffer + 8 * sizeof(char), comp_size);
            if (kb_uncompressed.empty()) {
                throw std::runtime_error("key block decompress failed empty");
            }
            key_block = kb_uncompressed.data();

            uint32_t adler32cs =
                    adler32checksum(key_block, static_cast<uint32_t>(decomp_size));
            assert(adler32cs == chksum);
            assert(kb_uncompressed.size() == decomp_size);
        } else {
            throw std::runtime_error("cannot determine the key block compress type");
        }

        // split key
        std::vector<key_list_item *> tlist =
                split_key_block(key_block, decomp_size, idx);
        return tlist;
    }

/**
 * decode the key block decode function, will invoke split key block
 *
 * this is for key block (not key block info)
 *
 * @param key_block_buffer
 * @param kb_buff_len
 * @return
 */
    int Mdict::decode_key_block(unsigned char *key_block_buffer,
                                unsigned long kb_buff_len) {
        int i = 0;

        for (long idx = 0; idx < static_cast<long>(this->key_block_info_list.size()); idx++) {
            unsigned long comp_size =
                    this->key_block_info_list[idx]->key_block_comp_size;
            unsigned long decomp_size =
                    this->key_block_info_list[idx]->key_block_decomp_size;
            unsigned long start_ofset = i;
            // unsigned long end_ofset = i + comp_size;
            // 4 bytes comp type
            char *key_block_comp_type = (char *)calloc(4, sizeof(char));
            memcpy(key_block_comp_type, key_block_buffer, 4 * sizeof(char));
            // 4 bytes adler checksum of decompressed key block
            // TODO  adler32 = unpack('>I', key_block_compressed[start + 4:start +
            // 8])[0]
            uint32_t chksum =
                    be_bin_to_u32(key_block_buffer + start_ofset + 4 * sizeof(char));

            unsigned char *key_block = nullptr;

            std::vector<uint8_t> kb_uncompressed; // note: ensure kb_uncompressed not
            // die when out of uncompress scope

            if ((key_block_comp_type[0] & 255) == 0) {
                // none compressed
                key_block = key_block_buffer + 8 * sizeof(char);
            } else if ((key_block_comp_type[0] & 255) == 1) {
                // 01000000
                // TODO lzo decompress

            } else if ((key_block_comp_type[0] & 255) == 2) {
                // zlib compress
                kb_uncompressed =
                        zlib_mem_uncompress(key_block_buffer + start_ofset + 8, comp_size);
                if (kb_uncompressed.empty() || kb_uncompressed.size() == 0) {
                    throw std::runtime_error("key block decompress failed");
                }
                key_block = kb_uncompressed.data();

                uint32_t adler32cs =
                        adler32checksum(key_block, static_cast<uint32_t>(decomp_size));
                assert(adler32cs == chksum);
                assert(kb_uncompressed.size() == decomp_size);
            } else {
                throw std::runtime_error("cannot determine the key block compress type");
            }

            // split key
            std::vector<key_list_item *> tlist =
                    split_key_block(key_block, decomp_size, idx);
            key_list.insert(key_list.end(), tlist.begin(), tlist.end());

            // TODO HERE append keys

            // next round
            i += comp_size;
        }
        assert(key_list.size() == this->entries_num);
        /// passed

        this->record_block_info_offset = this->key_block_info_start_offset +
                                         this->key_block_info_size +
                                         this->key_block_size;
        /// passed

        return 0;
    }

// note: kb_info_buff_len == key_block_info_compressed_size

/**
 * decode the record block
 * @param record_block_buffer
 * @param rb_len record block buffer length
 * @return
 */
    int Mdict::read_record_block_header() {
        /**
         * record block info section
         * decode the record block info section
         * [0:8/4]    - record blcok number
         * [8:16/4:8] - num entries the key-value entries number
         * [16:24/8:12] - record block info size
         * [24:32/12:16] - record block size
         */
        if (this->version >= 2.0) {
            record_block_info_size = 4 * 8;
        } else {
            record_block_info_size = 4 * 4;
        }

        char *record_info_buffer =
                (char *)calloc(record_block_info_size, sizeof(char));

        this->readfile(record_block_info_offset, record_block_info_size,
                       record_info_buffer);

        if (this->version >= 2.0) {
            record_block_number = be_bin_to_u64((unsigned char *)record_info_buffer);
            record_block_entries_number = be_bin_to_u64(
                    (unsigned char *)record_info_buffer + number_width * sizeof(char));
            record_block_header_size = be_bin_to_u64(
                    (unsigned char *)record_info_buffer + 2 * number_width * sizeof(char));
            record_block_size = be_bin_to_u64((unsigned char *)record_info_buffer +
                                              3 * number_width * sizeof(char));
        }

        free(record_info_buffer);
        assert(record_block_entries_number == entries_num);
        /// passed

        /**
         * record_block_header_list:
         * {
         * compressed size
         * decompressed size
         * }
         */

        char *record_header_buffer =
                (char *)calloc(record_block_header_size, sizeof(char));

        this->readfile(this->record_block_info_offset + record_block_info_size,
                       record_block_header_size, record_header_buffer);

        unsigned long comp_size = 0l;
        unsigned long uncomp_size = 0l;
        unsigned long size_counter = 0l;

        unsigned long comp_accu = 0l;
        unsigned long decomp_accu = 0l;

        for (unsigned long i = 0; i < record_block_number; ++i) {
            if (this->version >= 2.0) {
                comp_size =
                        be_bin_to_u64((unsigned char *)(record_header_buffer + size_counter));
                size_counter += number_width;
                uncomp_size =
                        be_bin_to_u64((unsigned char *)(record_header_buffer + size_counter));
                size_counter += number_width;

                this->record_header.push_back(new record_header_item(
                        i, comp_size, uncomp_size, comp_accu, decomp_accu));
                // ensure after push
                comp_accu += comp_size;
                decomp_accu += uncomp_size;
            } else {
                // TODO
            }
        }

        free(record_header_buffer);
        assert(this->record_header.size() == this->record_block_number);
        assert(size_counter == this->record_block_header_size);

        record_block_offset = record_block_info_offset + record_block_info_size +
                              record_block_header_size;
        /// passed
        return 0;
    }

    std::vector<std::pair<std::string, std::string>>
    Mdict::decode_record_block_by_rid(unsigned long rid /* record id */) {
        // record block start offset: record_block_offset
        uint64_t record_offset = this->record_block_offset;

        // key list index counter
        unsigned long i = 0l;

        std::vector<uint8_t> record_block_uncompressed_v;
        unsigned char *record_block_uncompressed_b;
        uint64_t checksum = 0l;

        unsigned long idx = rid;

        //  for (int idx = 0; idx < this->record_header.size(); idx++) {
        uint64_t comp_size = record_header[idx]->compressed_size;
        uint64_t uncomp_size = record_header[idx]->decompressed_size;
        uint64_t comp_accu = record_header[idx]->compressed_size_accumulator;
        uint64_t decomp_accu = record_header[idx]->decompressed_size_accumulator;
        uint64_t previous_end = 0;
        uint64_t previous_uncomp_size = 0;
        if (idx > 0) {
            previous_end = record_header[idx - 1]->decompressed_size_accumulator;
            previous_uncomp_size = record_header[idx - 1]->decompressed_size;
        }

        // Use std::vector for automatic memory management (RAII)
        std::vector<char> record_block_cmp_buffer(comp_size);

        this->readfile(record_offset + comp_accu, comp_size, record_block_cmp_buffer.data());

        // 4 bytes, compress type
        int comp_type = record_block_cmp_buffer[0] & 0xff;

        // 4 bytes adler32 checksum
        // We can read directly from the buffer
        checksum = be_bin_to_u32((unsigned char *)record_block_cmp_buffer.data() + 4);

        if (comp_type == 0 /* not compressed TODO*/) {
            throw std::runtime_error("uncompress block not support yet");
        } else {
            char *record_block_decrypted_buff;
            if (this->encrypt == ENCRYPT_RECORD_ENC /* record block encrypted */) {
                // TODO
                throw std::runtime_error("record encrypted not support yet");
            }
            record_block_decrypted_buff = record_block_cmp_buffer.data() + 8 * sizeof(char);
            // decompress
            if (comp_type == 1 /* lzo */) {
                throw std::runtime_error("lzo compress not support yet");
            } else if (comp_type == 2) {
                // zlib compress
                record_block_uncompressed_v =
                        zlib_mem_uncompress(record_block_decrypted_buff, comp_size);
                if (record_block_uncompressed_v.empty()) {
                    throw std::runtime_error("record block decompress failed size == 0");
                }
                record_block_uncompressed_b = record_block_uncompressed_v.data();
                uint32_t adler32cs = adler32checksum(record_block_uncompressed_b,
                                                     static_cast<uint32_t>(uncomp_size));
                
                if (record_block_uncompressed_v.size() != uncomp_size) {
                    throw std::runtime_error("record block decompress size mismatch");
                }
                if (adler32cs != checksum) {
                    throw std::runtime_error("record block checksum mismatch");
                }
            } else {
                throw std::runtime_error(
                        "cannot determine the record block compress type");
            }
        }

        // No need to free manual buffers anymore due to std::vector

        unsigned char *record_block = record_block_uncompressed_b;
        /**
         * 请注意，block 是会有很多个的，而每个block都可能会被压缩
         * 而 key_list中的 record_start,
         * key_text是相对每一个block而言的，end是需要每次解析的时候算出来的
         * 所有的record_start/length/end都是针对解压后的block而言的
         */

        std::vector<std::pair<std::string, std::string>> vec;

        while (i < this->key_list.size()) {
            // TODO OPTIMISE
            unsigned long record_start = key_list[i]->record_start;

            std::string key_text = key_list[i]->key_word;
            // start, skip the keys which not includes in record block
            if (record_start < decomp_accu) {
                i++;
                continue;
            }

            // end important: the condition should be lgt, because, the end bound will
            // be equal to uncompressed size
            // this part ensures the record match to key list bound
            if (record_start - decomp_accu >= uncomp_size) {
                break;
            }

            unsigned long upbound = uncomp_size; // - this->key_list[i]->record_start;
            unsigned long expect_end = 0;
            auto expect_start = this->key_list[i]->record_start - decomp_accu;
            if (i < this->key_list.size() - 1) {
                expect_end =
                        this->key_list[i + 1]->record_start - this->key_list[i]->record_start;
                expect_start = this->key_list[i]->record_start - decomp_accu;
            } else {

                expect_end =
                        this->record_block_size - (previous_end + previous_uncomp_size);
            }
            upbound = expect_end < upbound ? expect_end : upbound;

            std::string def;
            if (this->filetype == "MDD") {
                // FIX: Convert binary image/audio data to Hex String for safe JNI transfer
                const char* hex_map = "0123456789ABCDEF";
                unsigned char* data_ptr = (unsigned char*)record_block + expect_start;

                def.reserve(upbound * 2);
                for (size_t k = 0; k < upbound; ++k) {
                    unsigned char b = data_ptr[k];
                    def.push_back(hex_map[b >> 4]);
                    def.push_back(hex_map[b & 0x0F]);
                }
            } else {
                // --- FINAL FIX: ---
                // Ignore the (often incorrect) 'this->encoding' flag for MDX files.
                // The 'hiroshima' files are UTF-8, so we will *always* treat
                // MDX content as UTF-8.
                def = be_bin_to_utf8((char *)record_block, expect_start,
                                     upbound /* to delete null character*/);
            }

            std::pair<std::string, std::string> vp(key_text, def);
            vec.push_back(vp);
            i++;
        }

        //  assert(size_counter == record_block_size);
        return vec;
    }

// this function is used to decode the record block, it will read the record
// block from the file, avoid use this function
    int Mdict::decode_record_block() {
        // record block start offset: record_block_offset
        uint64_t record_offset = this->record_block_offset;

        uint64_t size_counter = 0l;

        // key list index counter
        unsigned long i = 0l;

        // record offset
        unsigned long offset = 0l;

        std::vector<uint8_t> record_block_uncompressed_v;
        unsigned char *record_block_uncompressed_b;
        uint64_t checksum = 0l;
        for (int idx = 0; idx < static_cast<int>(this->record_header.size()); idx++) {
            uint64_t comp_size = record_header[idx]->compressed_size;
            uint64_t uncomp_size = record_header[idx]->decompressed_size;
            char *record_block_cmp_buffer = (char *)calloc(comp_size, sizeof(char));
            this->readfile(record_offset, comp_size, record_block_cmp_buffer);
            //    putbytes(record_block_cmp_buffer, 8, true);
            // 4 bytes, compress type
            char *comp_type_b = (char *)calloc(4, sizeof(char));
            memcpy(comp_type_b, record_block_cmp_buffer, 4 * sizeof(char));
            //    putbytes(comp_type_b, 4, true);
            int comp_type = comp_type_b[0] & 0xff;
            // 4 bytes adler32 checksum
            char *checksum_b = (char *)calloc(4, sizeof(char));
            memcpy(checksum_b, record_block_cmp_buffer + 4, 4 * sizeof(char));
            checksum = be_bin_to_u32((unsigned char *)checksum_b);
            free(checksum_b);

            if (comp_type == 0 /* not compressed TODO*/) {
                throw std::runtime_error("uncompress block not support yet");
            } else {
                char *record_block_decrypted_buff;
                if (this->encrypt == ENCRYPT_RECORD_ENC /* record block encrypted */) {
                    // TODO
                    throw std::runtime_error("record encrypted not support yet");
                }
                record_block_decrypted_buff = record_block_cmp_buffer + 8 * sizeof(char);
                // decompress
                if (comp_type == 1 /* lzo */) {
                    throw std::runtime_error("lzo compress not support yet");
                } else if (comp_type == 2) {
                    // zlib compress
                    record_block_uncompressed_v =
                            zlib_mem_uncompress(record_block_decrypted_buff, comp_size);
                    if (record_block_uncompressed_v.empty()) {
                        throw std::runtime_error("record block decompress failed size == 0");
                    }
                    record_block_uncompressed_b = record_block_uncompressed_v.data();
                    uint32_t adler32cs = adler32checksum(
                            record_block_uncompressed_b, static_cast<uint32_t>(uncomp_size));
                    assert(adler32cs == checksum);
                    assert(record_block_uncompressed_v.size() == uncomp_size);
                } else {
                    throw std::runtime_error(
                            "cannot determine the record block compress type");
                }
            }

            free(comp_type_b);
            free(record_block_cmp_buffer);
            //    free(record_block_uncompressed_b); /* ensure not free twice*/

            // unsigned char* record_block = record_block_uncompressed_b;
            /**
             * 请注意，block 是会有很多个的，而每个block都可能会被压缩
             * 而 key_list中的 record_start,
             * key_text是相对每一个block而言的，end是需要每次解析的时候算出来的
             * 所有的record_start/length/end都是针对解压后的block而言的
             */
            while (i < this->key_list.size()) {
                unsigned long record_start = key_list[i]->record_start;
                std::string key_text = key_list[i]->key_word;
                if (record_start - offset >= uncomp_size) {
                    // overflow
                    break;
                }
                unsigned long record_end;
                if (i < this->key_list.size() - 1) {
                    record_end = this->key_list[i + 1]->record_start;
                } else {
                    record_end = uncomp_size + offset;
                }

                this->key_data.push_back(new record(
                        key_text, key_list[i]->record_start, this->encoding, record_offset,
                        comp_size, uncomp_size, comp_type, (this->encrypt == 1),
                        record_start - offset, record_end - offset));
                i++;
            }
            // offset += record_block.length
            offset += uncomp_size;
            size_counter += comp_size;
            record_offset += comp_size;

            //    break;
        }
        assert(size_counter == record_block_size);
        return 0;
    }

/**
 * decode the key block info
 * @param key_block_info_buffer the key block info buffer
 * @param kb_info_buff_len the key block buffer length
 * @param key_block_num the key block number
 * @param entries_num the entries number
 * @return
 */
    int Mdict::decode_key_block_info(char *key_block_info_buffer,
                                     unsigned long kb_info_buff_len,
                                     int key_block_num, int entries_num) {
        char *kb_info_buff = key_block_info_buffer;

        // key block info offset indicator
        unsigned long data_offset = 0;

        if (this->version >= 2.0) {
            // if version >= 2.0, use zlib compression
            assert(kb_info_buff[0] == 2);
            assert(kb_info_buff[1] == 0);
            assert(kb_info_buff[2] == 0);
            assert(kb_info_buff[3] == 0);
            byte *kb_info_decrypted = (unsigned char *)key_block_info_buffer;
            if (this->encrypt == ENCRYPT_KEY_INFO_ENC) {
                kb_info_decrypted = mdx_decrypt((byte *)kb_info_buff, kb_info_buff_len);
            }

            // finally, we needs to check adler32 checksum
            // key_block_info_compressed[4:8] => adler32 checksum
            //          uint32_t chksum = be_bin_to_u32((unsigned char*) (kb_info_buff +
            //          4));
            //          uint32_t adlercs = adler32checksum(key_block_info_uncomp,
            //          static_cast<uint32_t>(key_block_info_uncomp_len)) & 0xffffffff;
            //
            //          assert(chksum == adlercs);

            /// here passed, key block info is corrected
            // TODO decode key block info compressed into keys list

            // for version 2.0, will compress by zlib, lzo just just for 1.0
            // key_block_info_buff[0:8] => compress_type
            // TODO zlib decompress
            // TODO:
            // if the size of compressed data original data is unknown,
            // we malloc 8 size of source data len, we cannot estimate the original data
            // size
            // but currently, we know the size of key_block_info decompress size, so we
            // use this

            // note: we should uncompress key_block_info_buffer[8:] data, so we need
            // (decrypted + 8, and length -8)
            std::vector<uint8_t> decompress_buff =
                    zlib_mem_uncompress(kb_info_decrypted + 8, kb_info_buff_len - 8,
                                        this->key_block_info_decompress_size);
            /// uncompress successed
            assert(decompress_buff.size() == this->key_block_info_decompress_size);

            // get key block info list
            //          std::vector<key_block_info*> key_block_info_list;
            /// entries summary, every block has a lot of entries, the sum of entries
            /// should equals entries_number
            unsigned long num_entries_counter = 0;
            // key number counter
            unsigned long counter = 0;

            // current block entries
            unsigned long current_entries = 0;

            unsigned long previous_start_offset = 0;

            int byte_width = 1;
            int text_term = 0;
            if (this->version >= 2.0) {
                byte_width = 2;
                text_term = 1;
            }

            unsigned long comp_acc = 0l;
            unsigned long decomp_acc = 0l;
            while (counter < this->key_block_num) {
                if (this->version >= 2.0) {
                    auto bin_pointer =
                            decompress_buff.data() + data_offset * sizeof(uint8_t);
                    current_entries = be_bin_to_u64(bin_pointer);
                } else {
                    auto bin_pointer =
                            decompress_buff.data() + data_offset * sizeof(uint8_t);
                    current_entries = be_bin_to_u32(bin_pointer);
                }
                num_entries_counter += current_entries;

                // move offset
                // if version>= 2.0 move forward 8 bytes

                data_offset += this->number_width * sizeof(uint8_t);

                // first key size
                unsigned long first_key_size = 0;

                if (this->version >= 2.0) {
                    first_key_size = be_bin_to_u16(decompress_buff.data() +
                                                   data_offset * sizeof(uint8_t));
                } else {
                    first_key_size = be_bin_to_u8(decompress_buff.data() +
                                                  data_offset * sizeof(uint8_t));
                }
                data_offset += byte_width;

                // step_gap means first key start offset to first key end;
                int step_gap = 0;

                if (this->encoding == 1 /* encoding utf16 equals 1*/) {
                    step_gap = (first_key_size + text_term) * 2;
                } else {
                    step_gap = first_key_size + text_term;
                }

                // DECODE first CODE
                // TODO here minus the terminal character size(1), but we still not sure
                // should minus this or not
                std::string first_key;
                if (this->filetype == "MDX") {
                    first_key =
                            be_bin_to_utf8((char *)(decompress_buff.data() + data_offset), 0,
                                           (unsigned long)step_gap - text_term);
                } else {
                    unsigned char *utf16_point =
                            (unsigned char *)(decompress_buff.data() + data_offset);
                    unsigned long utf16_len = (unsigned long)step_gap - text_term;
                    unsigned char *utf8_buff =
                            (unsigned char *)calloc(utf16_len, sizeof(unsigned char));
                    utf16le_to_utf8(utf16_point, utf16_len - 1, utf8_buff, utf16_len);
                    first_key = std::string(reinterpret_cast<char *>(utf8_buff), utf16_len);
                    free(utf8_buff);
                }
                // move forward
                data_offset += step_gap;

                // the last key
                unsigned long last_key_size = 0;

                if (this->version >= 2.0) {
                    last_key_size = be_bin_to_u16(decompress_buff.data() +
                                                  data_offset * sizeof(uint8_t));
                } else {
                    last_key_size = be_bin_to_u8(decompress_buff.data() +
                                                 data_offset * sizeof(uint8_t));
                }
                data_offset += byte_width;

                if (this->encoding == 1 /* ENCODING_UTF16 */) {
                    step_gap = (last_key_size + text_term) * 2;
                } else {
                    step_gap = last_key_size + text_term;
                }

                std::string last_key;
                if (this->filetype == "MDX") {
                    last_key =
                            be_bin_to_utf8((char *)(decompress_buff.data() + data_offset), 0,
                                           (unsigned long)step_gap - text_term);
                } else {
                    unsigned char *utf16_point =
                            (unsigned char *)(decompress_buff.data() + data_offset);
                    unsigned long utf16_len = (unsigned long)step_gap - text_term;
                    unsigned char *utf8_buff =
                            (unsigned char *)calloc(utf16_len, sizeof(unsigned char));
                    utf16le_to_utf8(utf16_point, utf16_len - 1, utf8_buff, utf16_len);
                    last_key = std::string(reinterpret_cast<char *>(utf8_buff), utf16_len);
                    free(utf8_buff);
                }

                // move forward
                data_offset += step_gap;

                // ------------
                // key block part
                // ------------

                uint64_t key_block_compress_size = 0;
                if (version >= 2.0) {
                    key_block_compress_size =
                            be_bin_to_u64(decompress_buff.data() + data_offset);
                } else {
                    key_block_compress_size =
                            be_bin_to_u32(decompress_buff.data() + data_offset);
                }

                data_offset += this->number_width;

                uint64_t key_block_decompress_size = 0;

                if (version >= 2.0) {
                    key_block_decompress_size =
                            be_bin_to_u64(decompress_buff.data() + data_offset);
                } else {
                    key_block_decompress_size =
                            be_bin_to_u32(decompress_buff.data() + data_offset);
                }

                // entries offset move forward
                data_offset += this->number_width;

                key_block_info *kbinfo = new key_block_info(
                        first_key, last_key, previous_start_offset, key_block_compress_size,
                        key_block_decompress_size, comp_acc, decomp_acc);

                // adjust ofset
                previous_start_offset += key_block_compress_size;
                key_block_info_list.push_back(kbinfo);

                // key block counter
                counter += 1;
                // accumulate
                comp_acc += key_block_compress_size;
                decomp_acc += key_block_decompress_size;
                //          break;
            }
            assert(counter == this->key_block_num);

            // this allows us to handle some cases of malformed dictionaries without crashing.
            if (num_entries_counter != this->entries_num) {
                std::cerr << "[Warning] Key entry count mismatch: "
                          << num_entries_counter << " (found) vs "
                          << this->entries_num << " (expected)"
                          << std::endl;
            }


        } else {
            // doesn't compression
            throw std::logic_error("not implements yet");
        }

        this->key_block_body_start =
                this->key_block_info_start_offset + this->key_block_info_size;
        /// passed
        return 0;
    }

/**
 * read in the file from the file stream using fseeko for 64-bit offset support
 * @param offset the file start offset
 * @param len the byte length needs to read
 * @param buf the target buffer
 */
    void Mdict::readfile(uint64_t offset, uint64_t len, char *buf) {
        if (!this->file_ptr) return;

        // Use fseeko for 64-bit offset support (Android NDK supports this)
        fseeko(this->file_ptr, static_cast<off_t>(offset), SEEK_SET);
        fread(buf, 1, static_cast<size_t>(len), this->file_ptr);
    }

/***************************************
 * public part             *
 ***************************************/

/**
 * init the dictionary file
 */
    void Mdict::init() {
        // If file_ptr is not set, try to open the file using the filename (path-based constructor)
        if (!this->file_ptr) {
            if (!std::filesystem::exists(filename)) {
                throw std::runtime_error("File does not exist: " + filename);
            }
            this->file_ptr = fopen(this->filename.c_str(), "rb");
        }

        // Check if file_ptr is valid (opened in constructor or just now)
        if (!this->file_ptr) {
            throw std::runtime_error("File pointer is null (Open failed)");
        }

        /* indexing... */
        this->read_header();
        this->read_key_block_header();
        this->read_key_block_info();
        this->read_record_block_header();
        //  this->decode_record_block(); // don't use this function, it's too slow
    }

/**
 * find the key word includes in which block
 * @param phrase
 * @param start
 * @param end
 * @return
 */
    std::vector<long> Mdict::reduce_key_info_block(
            std::string phrase, unsigned long start,
            unsigned long end) {

        std::vector<long> matching_blocks;
        // Strip the search phrase ONCE
        std::string stripped_phrase = _s(phrase);

        for (size_t i = 0; i < end; ++i) {
            // FIX: Apply _s() to headers so we compare stripped-to-stripped
            std::string first_key = _s(this->key_block_info_list[i]->first_key);
            std::string last_key = _s(this->key_block_info_list[i]->last_key);

            // FIXED: Compare stripped vs stripped
            if (stripped_phrase.compare(first_key) >= 0 && stripped_phrase.compare(last_key) <= 0) {
                matching_blocks.push_back(i); // Add all matching blocks
            }
        }
        return matching_blocks; // Return the list
    }

    long Mdict::reduce_key_info_block_items_vector(
            std::vector<key_list_item *> wordlist,
            std::string phrase) { // non-recursive reduce implements
        unsigned long left = 0;
        unsigned long right = wordlist.size() - 1;
        unsigned long mid = 0;
        std::string word = _s(std::move(phrase));

        int comp = 0;
        while (left <= right) {
            mid = left + ((right - left) >> 1);
            // std::cout << "reduce1, mid = " << mid << ", left: " << left << ", right :
            // " <<  right << ", size: " << wordlist.size() << std::endl;
            if (mid >= wordlist.size()) {
                return -1;
            }
            comp = word.compare(_s(wordlist[mid]->key_word));
            if (comp == 0) {
                return mid;
            } else if (comp > 0) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return -1;
    }

/**
 *
 * @param wordlist
 * @param phrase
 * @return
 */
    long Mdict::reduce_record_block_offset(
            unsigned long record_start) { // non-recursive reduce implements
        // TODO OPTIMISE
        unsigned long left = 0l;
        unsigned long right = this->record_header.size() - 1;
        unsigned long mid = 0;
        while (left <= right) {
            mid = left + ((right - left) >> 1);
            if (record_start >=
                this->record_header[mid]->decompressed_size_accumulator) {
                left = mid + 1;
            } else if (record_start <
                       this->record_header[mid]->decompressed_size_accumulator) {
                right = mid - 1;
            }
        }
        return left - 1;
        return 0;
    }

    std::vector<std::string> Mdict::reduce_particial_keys_vector(
            std::vector<std::pair<std::string, std::string>> &vec, std::string phrase) {

        LOGD("--- reduce_particial_keys_vector (v4) ---");
        std::string word_to_find = _s(phrase);
        std::vector<std::string> definitions;
        std::vector<size_t> found_indices;

        // Priority 1: Scan for *unstripped* exact matches.
        for (size_t i = 0; i < vec.size(); ++i) {
            if (phrase.compare(vec[i].first) == 0) {
                definitions.push_back(vec[i].second); // Add the raw definition
                found_indices.push_back(i); // Mark this index as used
            }
        }

        // Priority 2: Scan for *stripped* matches.
        for (size_t i = 0; i < vec.size(); ++i) {
            bool already_added = false;
            for (size_t idx : found_indices) {
                if (i == idx) {
                    already_added = true;
                    break;
                }
            }

            if (!already_added) {
                if (word_to_find.compare(_s(vec[i].first)) == 0) {
                    definitions.push_back(vec[i].second); // Add the raw definition
                }
            }
        }

        LOGD("Found %zu definition fragments in this block.", definitions.size());
        return definitions;
    }

    std::string Mdict::locate(const std::string resource_name,
                              mdict_encoding_t encoding) {
        // find key item in key list
        auto it = std::find_if(this->key_list.begin(), this->key_list.end(),
                               [&](const key_list_item *item) {
                                   return item->key_word == resource_name;
                               });
        if (it != this->key_list.end()) {
            std::string key_word = (*it)->key_word;
            if (key_word == resource_name) {
                if ((*it)->record_start >= 0) {
                    // reduce search the record block index by word record start offset
                    unsigned long record_block_idx =
                            reduce_record_block_offset((*it)->record_start);
                    // decode recode by record index
                    auto vec = decode_record_block_by_rid(record_block_idx);
                    // reduce the definition by word
                    std::vector<std::string> defs = reduce_particial_keys_vector(vec, resource_name);

                    if (defs.empty()) {
                        return std::string(""); // Not found
                    }
                    std::string def = defs[0]; // 'locate' only expects one result

                    auto treated_output = trim_nulls(def);

                    if (encoding == MDICT_ENCODING_HEX) {
                        return treated_output; // Return raw hex string
                    } else {
                        return base64_from_hex(
                                treated_output); // Return base64 encoded string
                    }
                }
                return std::string("");
            }
        }
        return std::string("");
    }

    std::string Mdict::lookup0(const std::string word) {
        try {

            auto it = std::find_if(
                    this->key_list.begin(), this->key_list.end(),
                    [&](const key_list_item *item) { return item->key_word == word; });
            if (it != this->key_list.end()) {
                std::string key_word = (*it)->key_word;
                if (key_word == word) {
                    if ((*it)->record_start >= 0) {
                        // reduce search the record block index by word record start offset
                        unsigned long record_block_idx =
                                reduce_record_block_offset((*it)->record_start);
                        // decode recode by record index
                        auto vec = decode_record_block_by_rid(record_block_idx);
                        // reduce the definition by word
                        std::vector<std::string> defs = reduce_particial_keys_vector(vec, word);

                        if (defs.empty()) {
                            return std::string(""); // Not found
                        }
                        std::string def = defs[0]; // 'lookup0' only expects one result

                        auto treated_output = trim_nulls(def);

                        return treated_output;
                    }
                    return std::string("");
                }
            }
            return std::string("");


        } catch (std::exception &e) {
            std::cout << "lookup error: " << e.what() << std::endl;
        }
        return std::string();
    }


/**
 * look the file by word
 * @param word the searching word
 * @return
 */
    std::vector<std::string> Mdict::lookup(const std::string word) {
        LOGD("--- New Lookup (Vector) ---");
        LOGD("Lookup received: '%s'", word.c_str());

        try {
            if (this->filetype == "MDD") {
                // MDD files (resources) usually only have one entry per key.
                // Wrap the single result in a vector.
                std::string result = this->locate(word, MDICT_ENCODING_HEX);
                if (result.empty()) {
                    return {};
                }
                return {result};
            }

            // --- NEW LOGIC (v5 - Return All) ---

            // 1. Find all matching keys in the complete key_list and group by record block
            std::map<unsigned long, std::vector<key_list_item*>> record_block_map;
            std::string stripped_word = _s(word);

            for (key_list_item* item : this->key_list) {
                if (item->key_word == word || _s(item->key_word) == stripped_word) {
                    unsigned long record_block_idx = reduce_record_block_offset(item->record_start);
                    record_block_map[record_block_idx].push_back(item);
                }
            }

            if (record_block_map.empty()) {
                LOGD("No matching keys found in the entire key_list for '%s'", word.c_str());
                return {};
            }

            // 2. Decode blocks and collect all raw definition strings
            std::vector<std::string> all_results;

            for (auto const& [record_idx, items] : record_block_map) {
                LOGD("Decoding record block %lu for %zu keys", record_idx, items.size());

                auto vec = decode_record_block_by_rid(record_idx);

                // Get all raw definitions (HTML or @@@LINKs)
                std::vector<std::string> defs = reduce_particial_keys_vector(vec, word);
                
                // Append all found definitions to the master list
                all_results.insert(all_results.end(), defs.begin(), defs.end());
            }

            LOGD("Total results found: %zu", all_results.size());
            return all_results;

        } catch (std::exception &e) {
            std::cout << "lookup error: " << e.what() << std::endl;
        }
        return {};
    }

    std::string Mdict::parse_definition(const std::string word,
                                        unsigned long record_start) {
        // reduce search the record block index by word record start offset
        unsigned long record_block_idx = reduce_record_block_offset(record_start);
        // decode recode by record index
        auto vec = decode_record_block_by_rid(record_block_idx);
        // reduce the definition by word
        std::vector<std::string> defs = reduce_particial_keys_vector(vec, word);

        if (defs.empty()) {
            return std::string(""); // Not found
        }

        // parse_definition, like lookup0, should only return the first match.
        return defs[0];
    }

/**
 * look the file by word
 * @param word the searching word
 * @return
 */
    std::vector<key_list_item *> Mdict::keyList() { return this->key_list; }

    bool Mdict::endsWith(std::string const &fullString, std::string const &ending) {
        if (fullString.length() >= ending.length()) {
            return (0 == fullString.compare(fullString.length() - ending.length(),
                                            ending.length(), ending));
        } else {
            return false;
        }
    }
// ... all other functions are above this ...

/**
 * suggest simuler word which matches the prefix
 * @param word the word's prefix
 * @return
 */
    std::vector<std::string> Mdict::suggest(const std::string word) {
        std::vector<std::string> suggestions;
        if (word.empty()) return suggestions;

        std::string prefix = word;
        // Create a lowercase version of the prefix for comparison
        std::transform(prefix.begin(), prefix.end(), prefix.begin(), ::tolower);

        const size_t max_suggestions = 50;

        // Optimization: Use binary search to find the first key >= prefix
        // key_list is sorted by key_word (usually).
        // We need a custom comparator for case-insensitive comparison.
        auto it = std::lower_bound(this->key_list.begin(), this->key_list.end(), prefix,
            [](const key_list_item* item, const std::string& val) {
                std::string key = item->key_word;
                std::transform(key.begin(), key.end(), key.begin(), ::tolower);
                return key < val;
            });

        // Iterate from the found position
        for (; it != this->key_list.end(); ++it) {
            std::string key = (*it)->key_word;
            std::string lower_key = key;
            std::transform(lower_key.begin(), lower_key.end(), lower_key.begin(), ::tolower);

            // Check if the key still starts with the prefix
            if (lower_key.rfind(prefix, 0) == 0) {
                suggestions.push_back(key);
                if (suggestions.size() >= max_suggestions) {
                    break;
                }
            } else {
                // Since the list is sorted, once we find a key that doesn't match the prefix,
                // we can stop searching (optimization).
                // However, we must be careful about case sensitivity sorting order vs our lowercase comparison.
                // Generally, if "apple" < "banana", then "apple..." < "banana...".
                // But strictly speaking, we should only break if the key is "greater" than prefix in a way that precludes matching.
                // For safety and simplicity in this "suggest" context, checking the next 50-100 items is usually enough.
                // But to be strictly correct with std::lower_bound, we should stop if the prefix doesn't match.
                // Let's assume standard dictionary sorting.
                
                // If the current key is lexicographically larger than prefix and doesn't start with it,
                // we are done.
                 if (lower_key > prefix) {
                     break; 
                 }
            }
        }
        
        return suggestions;
    }

    // Helper to convert UTF-8 to wstring (UTF-32 on Linux/Android)
    std::wstring utf8_to_wstring(const std::string& str) {
        std::wstring wstr;
        wstr.reserve(str.length()); // Optimization: Reserve memory
        size_t i = 0;
        size_t len = str.length();
        while (i < len) {
            unsigned char c = str[i];
            if (c < 0x80) {
                wstr += (wchar_t)c;
                i++;
            } else if ((c & 0xE0) == 0xC0) {
                if (i + 1 < len) {
                    wstr += (wchar_t)(((c & 0x1F) << 6) | (str[i + 1] & 0x3F));
                    i += 2;
                } else break;
            } else if ((c & 0xF0) == 0xE0) {
                if (i + 2 < len) {
                    wstr += (wchar_t)(((c & 0x0F) << 12) | ((str[i + 1] & 0x3F) << 6) | (str[i + 2] & 0x3F));
                    i += 3;
                } else break;
            } else if ((c & 0xF8) == 0xF0) {
                if (i + 3 < len) {
                    wstr += (wchar_t)(((c & 0x07) << 18) | ((str[i + 1] & 0x3F) << 12) | ((str[i + 2] & 0x3F) << 6) | (str[i + 3] & 0x3F));
                    i += 4;
                } else break;
            } else {
                i++; // Invalid, skip
            }
        }
        return wstr;
    }

    std::vector<std::string> Mdict::regex_suggest(const std::string regex_str) {
        std::vector<std::string> suggestions;
        
        if (regex_str.empty()) return suggestions;

        const size_t max_suggestions = 50;

        // --- 1. Parse Regex for Optimizations ---
        std::string start_prefix = "";
        std::string required_substring = "";
        bool has_start_anchor = false;

        if (regex_str[0] == '^') {
            has_start_anchor = true;
            // Extract prefix: ^abc... until special char
            size_t i = 1;
            while (i < regex_str.length()) {
                char c = regex_str[i];
                // Stop at any regex special char (simplified check)
                if (c == '.' || c == '*' || c == '+' || c == '?' || c == '(' || c == ')' || 
                    c == '[' || c == ']' || c == '{' || c == '}' || c == '|' || c == '\\' || c == '$') {
                    break;
                }
                start_prefix += c;
                i++;
            }
        }

        // Extract longest literal substring for pre-filtering
        // e.g. ".*tion$" -> "tion"
        std::string current_literal;
        for (char c : regex_str) {
             if (c == '^' || c == '$' || c == '.' || c == '*' || c == '+' || c == '?' || 
                 c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}' || 
                 c == '|' || c == '\\') {
                 if (current_literal.length() > required_substring.length()) {
                     required_substring = current_literal;
                 }
                 current_literal = "";
             } else {
                 current_literal += c;
             }
        }
        if (current_literal.length() > required_substring.length()) {
            required_substring = current_literal;
        }

        // Lowercase for case-insensitive comparison
        std::string start_prefix_lower = start_prefix;
        std::transform(start_prefix_lower.begin(), start_prefix_lower.end(), start_prefix_lower.begin(), ::tolower);
        
        std::string required_substring_lower = required_substring;
        std::transform(required_substring_lower.begin(), required_substring_lower.end(), required_substring_lower.begin(), ::tolower);

        LOGD("Regex Opt: Prefix='%s', Substring='%s'", start_prefix.c_str(), required_substring.c_str());

        // --- 2. Compile Regex ---
        std::wstring wregex_str = utf8_to_wstring(regex_str);
        std::wregex re;
        try {
            re = std::wregex(wregex_str, std::regex_constants::icase);
        } catch (const std::regex_error& e) {
            LOGE("Invalid regex: %s", regex_str.c_str());
            return suggestions;
        }

        // --- 3. Determine Start Iterator ---
        auto it = this->key_list.begin();
        
        if (has_start_anchor && !start_prefix.empty()) {
            // Optimization 1: Binary Search for Prefix
            it = std::lower_bound(this->key_list.begin(), this->key_list.end(), start_prefix,
                [](const key_list_item* item, const std::string& val) {
                    std::string key = item->key_word;
                    // We need a loose comparison because key_list might be mixed case
                    // But standard string comparison is usually fine for finding the "start" block
                    // Let's use case-insensitive for safety since we want to find "Apple" with "^apple"
                    std::transform(key.begin(), key.end(), key.begin(), ::tolower);
                    std::string val_lower = val;
                    std::transform(val_lower.begin(), val_lower.end(), val_lower.begin(), ::tolower);
                    return key < val_lower;
                });
        }

        // --- 4. Iterate and Filter ---
        size_t checked_count = 0;
        for (; it != this->key_list.end(); ++it) {
            std::string key = (*it)->key_word;
            std::string key_lower = key;
            std::transform(key_lower.begin(), key_lower.end(), key_lower.begin(), ::tolower);

            // Optimization 1 Check: Early Exit
            if (has_start_anchor && !start_prefix_lower.empty()) {
                // If the key is now lexicographically smaller than prefix (shouldn't happen with lower_bound)
                // or if it doesn't start with prefix AND is lexicographically larger
                // Actually, just check if it starts with prefix.
                // If it doesn't start with prefix, AND it is "after" the prefix, we can stop.
                
                if (key_lower.rfind(start_prefix_lower, 0) != 0) {
                     // Mismatch. Since keys are sorted, if key_lower > start_prefix_lower, we are done.
                     if (key_lower > start_prefix_lower) {
                         break; 
                     }
                     // If key_lower < start_prefix_lower (e.g. "Abc" vs "abc" sorting quirks), continue.
                     continue; 
                }
            }

            // Optimization 2 Check: Literal Pre-filtering
            if (!required_substring_lower.empty()) {
                if (key_lower.find(required_substring_lower) == std::string::npos) {
                    continue; // Skip regex check
                }
            }

            // Final Check: Full Regex
            std::wstring wkey = utf8_to_wstring(key);
            if (std::regex_search(wkey, re)) {
                suggestions.push_back(key);
                if (suggestions.size() >= max_suggestions) {
                    break;
                }
            }
            checked_count++;
            // Safety break for very broad queries to prevent freezing UI
            if (checked_count > 5000 && suggestions.empty() && !has_start_anchor) {
                 // If we scanned 5000 items and found nothing, and we aren't anchored, maybe stop?
                 // Or just let it run. Let's limit total checks if we have some results.
            }
            if (checked_count > 20000) break; // Hard limit to prevent ANR
        }
        
        LOGD("Regex Search: Checked %zu items, found %zu", checked_count, suggestions.size());
        return suggestions;
    }

    std::vector<std::string> Mdict::fulltext_search(const std::string query, std::function<void(float)> progress_callback) {
        std::vector<std::string> suggestions;
        std::wstring wquery = utf8_to_wstring(query);
        // Lowercase the query for case-insensitive check
        std::transform(wquery.begin(), wquery.end(), wquery.begin(), ::towlower);

        const size_t max_suggestions = 50;
        size_t blocks_checked = 0;
        size_t total_blocks = this->record_header.size();

        // Iterate over ALL record blocks
        // record_header contains info for each block.
        for (size_t rid = 0; rid < total_blocks; ++rid) {
            if (progress_callback && rid % 5 == 0) { // Report every 5 blocks
                 progress_callback(static_cast<float>(rid) / total_blocks);
            }
            try {
                // Decode the block. This returns a vector of <key, definition> pairs.
                // This is expensive!
                std::vector<std::pair<std::string, std::string>> block_entries = this->decode_record_block_by_rid(rid);

                for (const auto& entry : block_entries) {
                    // entry.first is Headword
                    // entry.second is Definition (HTML/Text)

                    // Convert definition to wstring for search
                    std::wstring wdef = utf8_to_wstring(entry.second);
                    
                    // We need to lowercase the definition for case-insensitive search
                    // Optimization: Search without lowercasing first? No, that misses cases.
                    // We have to pay the price for full text search.
                    
                    // To avoid copying the huge wdef, we can iterate? 
                    // transform is in-place.
                    std::transform(wdef.begin(), wdef.end(), wdef.begin(), ::towlower);

                    if (wdef.find(wquery) != std::wstring::npos) {
                        suggestions.push_back(entry.first);
                        if (suggestions.size() >= max_suggestions) {
                            return suggestions;
                        }
                    }
                }
                blocks_checked++;
            } catch (const std::exception& e) {
                // Log the error but continue searching other blocks
                LOGE("fulltext_search: Error decoding block %zu: %s. Skipping.", rid, e.what());
                continue;
            } catch (...) {
                LOGE("fulltext_search: Unknown error decoding block %zu. Skipping.", rid);
                continue;
            }
        }
        
        LOGD("Full-text search checked %zu blocks, found %zu results", blocks_checked, suggestions.size());
        return suggestions;
    }


} // namespace mdict