/*
 * Copyright (c) 2025-Present
 * All rights reserved.
 *
 * This code is licensed under the BSD 3-Clause License.
 * See the LICENSE file for details.
 */

#include "include/binutils.h"

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <locale>  // For std::wstring_convert
// #include "deps/miniz/miniz.h"
#include <zlib.h>

#include <codecvt>
#include <string>
#include <vector>

#include "encode/gbk_table.h"
#include "encode/big5_table.h"

using namespace std;

char const hex_chars[16] = {'0', '1', '2', '3', '4', '5', '6', '7',
                            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

uint32_t be_bin_to_u32(const unsigned char *bin /* 4 bytes char array  */) {
  uint32_t n = 0;
  for (int i = 0; i < 3; i++) {
    n = n | (unsigned int)bin[i];
    n = n << 8;
  }
  n = n | (unsigned int)bin[3];
  return n;
}

uint64_t be_bin_to_u64(const unsigned char *bin /* 8 bytes char array  */) {
  uint64_t n = 0;
  for (int i = 0; i < 7; i++) {
    n = n | (unsigned int)bin[i];
    n = n << 8;
  }
  n = n | (unsigned int)bin[7];
  return n;
}

uint16_t be_bin_to_u16(const unsigned char *bin /* 8 bytes char array  */) {
  uint16_t n = 0;

  for (int i = 0; i < 1; i++) {
    n = n | (uint16_t)bin[i];
    n = n << 8;
  }
  n = n | (uint16_t)bin[1];
  return n;
}

uint8_t be_bin_to_u8(const unsigned char *bin /* 8 bytes char array  */) {
  return bin[0] & 255;
}

void putbytes(const char *bytes, int len, bool hex = true,
              unsigned long startofset) {
  int maxlen = 100;
  if (hex) {
    std::printf("<Buffer ");
    for (int i = 0; i < ((len - 1) > maxlen ? maxlen : (len - 1)); i++) {
      std::printf("%02x ", bytes[i] & 255);
      //        std::printf("%02x(%d) ", bytes[i] & 255,bytes[i] & 255);
    }
    std::printf("%02x", bytes[len - 1] & 255);

    std::printf("> (%ld,%d)\n", startofset, len);
    //    std::printf(">\n");
  } else {
    std::printf("<Buffer ");
    for (int i = 0; i < len - 1; i++) {
      std::printf("%d ", bytes[i] & 255);
    }
    std::printf("%d", bytes[len - 1] & 255);
    std::printf("> (%d)\n", len);
  }
}

/*****************************************************************
 *                                                               *
 *                        ENCODING METHODS                       *
 *                                                               *
 *****************************************************************/

// 工具包装器，用于字符转换 为wstring/wbuffer适配绑定到 locale 的平面
template <class Facet>
struct usable_facet : public Facet {
 public:
  using Facet::Facet;  // inherit constructors
  ~usable_facet() {}

  // workaround for compilers without inheriting constructors:
  // template <class ...Args> usable_facet(Args&& ...args) :
  // Facet(std::forward<Args>(args)...) {}
};

template <typename internT, typename externT, typename stateT>
using facet_codecvt = usable_facet<std::codecvt<internT, externT, stateT> >;

/*************************************************
 * little-endian binary to utf16 to utf8 string   *
 **************************************************/

// Helper function to convert UTF-16 to UTF-8
std::string utf16_to_utf8(const std::u16string &utf16) {
  std::string utf8;
  utf8.reserve(utf16.length() *
               3);  // UTF-8 can be up to 3 bytes per UTF-16 char

  for (char16_t c : utf16) {
    if (c <= 0x7F) {
      // ASCII character
      utf8.push_back(static_cast<char>(c));
    } else if (c <= 0x7FF) {
      // 2-byte UTF-8
      utf8.push_back(static_cast<char>(0xC0 | (c >> 6)));
      utf8.push_back(static_cast<char>(0x80 | (c & 0x3F)));
    } else {
      // 3-byte UTF-8
      utf8.push_back(static_cast<char>(0xE0 | (c >> 12)));
      utf8.push_back(static_cast<char>(0x80 | ((c >> 6) & 0x3F)));
      utf8.push_back(static_cast<char>(0x80 | (c & 0x3F)));
    }
  }
  return utf8;
}

// binary to utf16->utf8
std::string le_bin_utf16_to_utf8(const char *bytes, int offset, int len) {
  char *cbytes = (char *)calloc(len, sizeof(char));
  if (cbytes == nullptr) {
    return "";
  }
  // TODO insecure
  std::memcpy(cbytes, bytes + (offset * sizeof(char)), len * sizeof(char));
  // convert char* to char16_t*
  char16_t *wcbytes = reinterpret_cast<char16_t *>(cbytes);

  std::u16string u16 = std::u16string(wcbytes);
  std::string u8 = utf16_to_utf8(u16);

  if (len > 0) std::free(cbytes);
  return u8;
}

std::string be_bin_to_utf8(const char *bytes, unsigned long offset,
                           unsigned long len) {
  std::string u8(bytes + offset * sizeof(char), len);
  return u8;
}

std::string gbk_to_utf8(const char* bytes, unsigned long offset, unsigned long len) {
    std::string utf8;
    utf8.reserve(len * 2);
    const unsigned char* p = (const unsigned char*)bytes + offset;
    const unsigned char* end = p + len;
    while (p < end) {
        if (*p < 0x80) {
            if (*p == 0) { p++; continue; }
            utf8.push_back(*p++);
        } else if (p + 1 < end) {
            uint16_t idx = (*p << 8) | *(p + 1);
            p += 2;
            uint16_t u16 = gbk_to_utf16[idx];
            if (u16 == 0) u16 = '?';
            if (u16 <= 0x7F) {
                utf8.push_back(static_cast<char>(u16));
            } else if (u16 <= 0x7FF) {
                utf8.push_back(static_cast<char>(0xC0 | (u16 >> 6)));
                utf8.push_back(static_cast<char>(0x80 | (u16 & 0x3F)));
            } else {
                utf8.push_back(static_cast<char>(0xE0 | (u16 >> 12)));
                utf8.push_back(static_cast<char>(0x80 | ((u16 >> 6) & 0x3F)));
                utf8.push_back(static_cast<char>(0x80 | (u16 & 0x3F)));
            }
        } else {
            p++;
        }
    }
    return utf8;
}

std::string big5_to_utf8(const char* bytes, unsigned long offset, unsigned long len) {
    std::string utf8;
    utf8.reserve(len * 2);
    const unsigned char* p = (const unsigned char*)bytes + offset;
    const unsigned char* end = p + len;
    while (p < end) {
        if (*p < 0x80) {
            if (*p == 0) { p++; continue; }
            utf8.push_back(*p++);
        } else if (p + 1 < end) {
            uint16_t idx = (*p << 8) | *(p + 1);
            p += 2;
            uint16_t u16 = big5_to_utf16[idx];
            if (u16 == 0) u16 = '?';
            if (u16 <= 0x7F) {
                utf8.push_back(static_cast<char>(u16));
            } else if (u16 <= 0x7FF) {
                utf8.push_back(static_cast<char>(0xC0 | (u16 >> 6)));
                utf8.push_back(static_cast<char>(0x80 | (u16 & 0x3F)));
            } else {
                utf8.push_back(static_cast<char>(0xE0 | (u16 >> 12)));
                utf8.push_back(static_cast<char>(0x80 | ((u16 >> 6) & 0x3F)));
                utf8.push_back(static_cast<char>(0x80 | (u16 & 0x3F)));
            }
        } else {
            p++;
        }
    }
    return utf8;
}

std::string be_bin_to_utf16(const char *bytes, unsigned long offset,
                            unsigned long len) {
  std::string su8(bytes + offset * sizeof(char), len);
  char *hex_target = (char *)calloc(2 * len + 1, sizeof(char));
  bintohex(bytes + offset * sizeof(char), len, hex_target);
  std::string u16(hex_target, 2 * len + 1);
  free(hex_target);

  return u16;
}

// slice srcByte to distByte
// ensure srcByte.length > len
int bin_slice(const char *srcByte, int srcByteLen, int offset, int len,
              char *distByte) {
  if (offset < 0 || offset > srcByteLen - 1) {
    return -1;
  }
  if (offset + len > srcByteLen) {
    // invalid offset & length
    return -2;
  }
  // ensure distByte has malloced
  for (int i = 0; i < len; ++i) {
    (distByte)[i] = srcByte[i + offset];
  }
  return 0;
}

// char const hex_chars[16] = { '0', '1', '2', '3', '4', '5', '6', '7', '8',
// '9', 'A', 'B', 'C', 'D', 'E', 'F' };

int bintohex(const char *bin, unsigned long len, char *target) {
  unsigned long i = 0;
  for (i = 0; i < len; i++) {
    char const byte = bin[i];

    target[2 * i] = hex_chars[(byte & 0xF0) >> 4];
    target[2 * i + 1] = hex_chars[(byte & 0x0F)];
  }
  return i;
}
