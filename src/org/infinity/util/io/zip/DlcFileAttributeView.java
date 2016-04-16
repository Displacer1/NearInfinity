// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information
//
// ----------------------------------------------------------------------------
//
// Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
//
//   - Redistributions of source code must retain the above copyright
//     notice, this list of conditions and the following disclaimer.
//
//   - Redistributions in binary form must reproduce the above copyright
//     notice, this list of conditions and the following disclaimer in the
//     documentation and/or other materials provided with the distribution.
//
//   - Neither the name of Oracle nor the names of its
//     contributors may be used to endorse or promote products derived
//     from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
// IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
// PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
// EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
// PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
// PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
// LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
// NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package org.infinity.util.io.zip;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FileAttributeView implementation for DLC archives in zip format.
 */
public class DlcFileAttributeView implements BasicFileAttributeView
{
  static final String VIEW_BASIC  = "basic";
  static final String VIEW_ZIP    = "zip";

  private static enum AttrID {
    size,
    creationTime,
    lastAccessTime,
    lastModifiedTime,
    isDirectory,
    isRegularFile,
    isSymbolicLink,
    isOther,
    fileKey,
    compressedSize,
    crc,
    method
  };

  private final DlcPath path;
  private final boolean isZipView;

  private DlcFileAttributeView(DlcPath path, boolean isZipView)
  {
    this.path = path;
    this.isZipView = isZipView;
  }

  @SuppressWarnings("unchecked")
  static <V extends FileAttributeView> V get(DlcPath path, Class<V> type)
  {
    if (type == null) {
      throw new NullPointerException();
    }
    if (type == BasicFileAttributeView.class) {
      return (V)new DlcFileAttributeView(path, false);
    }
    if (type == DlcFileAttributeView.class) {
      return (V)new DlcFileAttributeView(path, true);
    }
    return null;
  }

  static DlcFileAttributeView get(DlcPath path, String type)
  {
    if (type == null) {
      throw new NullPointerException();
    }
    if (type.equals(VIEW_BASIC)) {
      return new DlcFileAttributeView(path, false);
    }
    if (type.equals(VIEW_ZIP)) {
      return new DlcFileAttributeView(path, true);
    }
    return null;
  }

  @Override
  public String name()
  {
    return isZipView ? VIEW_ZIP : VIEW_BASIC;
  }

  @Override
  public DlcFileAttributes readAttributes() throws IOException
  {
    return path.getAttributes();
  }

  @Override
  public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
      throws IOException
  {
    path.setTimes(lastModifiedTime, lastAccessTime, createTime);
  }


  void setAttribute(String attribute, Object value) throws IOException
  {
    try {
      if (AttrID.valueOf(attribute) == AttrID.lastModifiedTime) {
        setTimes((FileTime) value, null, null);
      }
      if (AttrID.valueOf(attribute) == AttrID.lastAccessTime) {
        setTimes(null, (FileTime) value, null);
      }
      if (AttrID.valueOf(attribute) == AttrID.creationTime) {
        setTimes(null, null, (FileTime) value);
      }
      return;
    } catch (IllegalArgumentException x) {
    }
    throw new UnsupportedOperationException(
        "'" + attribute + "' is unknown or read-only attribute");
  }

  Map<String, Object> readAttributes(String attributes) throws IOException
  {
    DlcFileAttributes dfas = readAttributes();
    LinkedHashMap<String, Object> map = new LinkedHashMap<>();
    if ("*".equals(attributes)) {
      for (AttrID id : AttrID.values()) {
        try {
          map.put(id.name(), attribute(id, dfas));
        } catch (IllegalArgumentException x) {
        }
      }
    } else {
      String[] as = attributes.split(",");
      for (String a : as) {
        try {
          map.put(a, attribute(AttrID.valueOf(a), dfas));
        } catch (IllegalArgumentException x) {
        }
      }
    }
    return map;
  }

  Object attribute(AttrID id, DlcFileAttributes dfas)
  {
    switch (id) {
      case size:
        return dfas.size();
      case creationTime:
        return dfas.creationTime();
      case lastAccessTime:
        return dfas.lastAccessTime();
      case lastModifiedTime:
        return dfas.lastModifiedTime();
      case isDirectory:
        return dfas.isDirectory();
      case isRegularFile:
        return dfas.isRegularFile();
      case isSymbolicLink:
        return dfas.isSymbolicLink();
      case isOther:
        return dfas.isOther();
      case fileKey:
        return dfas.fileKey();
      case compressedSize:
        if (isZipView)
          return dfas.compressedSize();
        break;
      case crc:
        if (isZipView)
          return dfas.crc();
        break;
      case method:
        if (isZipView)
          return dfas.method();
        break;
    }
    return null;
  }
}
