<h1>Vibedict</h1>

<div align="center"><a href="./"><img src="./banner.svg" alt="Vibedict" style="width: 70%;"></a></div>

## Description

An Android dictionary application developed with the assistance of artificial intelligence.

## Features
<li><b>Native Parsing</b>: The app uses a native C++ parsing engine for mdd and mdx files, based on the <a href="https://github.com/dictlab/mdict-cpp">mdict-cpp library</a>.</li>
<li><b>Zero-Copy Memory Management</b>: The app passes the File Descriptor (FD) directly to the native layer. This allows the underlying C++ engine to access the file via memory mapping (mmap) or direct file system calls, ensuring extreme space efficiency and fast startup times regardless of the dictionary size.</li>
<li><b>Hybrid Search Architecture</b>: The application supports a unified search experience that integrates local offline dictionaries, web search engines and AI querying.</li>

## Limitations
<li>Incomplete localisation</li>
<li>Errors, crashes and any instability issues may be expected.</li>

## Notice
<li>This app is still in the early stages. Some functions may not be implemented or working as intended.</li>
<li>This app is largely built with the help of artificial intelligence.</li>

##

<a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/w4lt3rmel0n/Vibedict/">
  <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" width="150">
</a>
