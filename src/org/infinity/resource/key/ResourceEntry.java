// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.key;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Locale;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import org.infinity.NearInfinity;
import org.infinity.gui.BrowserMenuBar;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.are.AreResource;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.itm.ItmResource;
import org.infinity.resource.other.EffResource;
import org.infinity.resource.other.VvcResource;
import org.infinity.resource.pro.ProResource;
import org.infinity.resource.spl.SplResource;
import org.infinity.resource.sto.StoResource;
import org.infinity.search.SearchOptions;
import org.infinity.util.io.StreamUtils;

public abstract class ResourceEntry implements Comparable<ResourceEntry>
{
  // list of file extensions not shown in the resource tree
  private static final HashSet<String> skippedExtensions = new HashSet<String>();

  static {
    skippedExtensions.add("BAK");
    skippedExtensions.add("BIF");
  }

  private String searchString;

  static int[] getLocalFileInfo(Path file)
  {
    if (file != null && Files.isRegularFile(file)) {
      try (SeekableByteChannel ch = Files.newByteChannel(file, StandardOpenOption.READ)) {
        ByteBuffer bb = StreamUtils.getByteBuffer((int)ch.size());
        if (ch.read(bb) < ch.size()) {
          throw new IOException();
        }
        bb.position(0);

        String sig, ver;
        if (bb.remaining() >= 8) {
          byte[] buf = new byte[4];
          bb.get(buf);
          sig = new String(buf);
          bb.get(buf);
          ver = new String(buf);
        } else {
          sig = ver = "";
        }
        if ("TIS ".equals(sig) && "V1  ".equals(ver)) {
          if (bb.limit() > 16) {
            int v1 = bb.getInt(8);
            int v2 = bb.getInt(12);
            return new int[]{ v1, v2 };
          } else {
            throw new IOException("Unexpected end of file");
          }
        } else if (file.getFileName().toString().toUpperCase(Locale.ENGLISH).endsWith(".TIS")) {
          int tileSize = 0;
          if (bb.remaining() > 16) {
            tileSize = bb.getInt(12);
          }
          if (tileSize > 0) {
            int tileCount = bb.limit() / tileSize;
            return new int[]{ tileCount, tileSize };
          } else {
            throw new Exception("Invalid TIS tile size");
          }
        } else {
          return new int[]{ (int)ch.size() };
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o)
  {
    if (o == this) {
      return true;
    } else if (o instanceof ResourceEntry) {
      return getResourceName().equalsIgnoreCase(((ResourceEntry)o).getResourceName());
    }
    return false;
  }

// --------------------- Begin Interface Comparable ---------------------

  @Override
  public int compareTo(ResourceEntry entry)
  {
    if (entry == this) {
      return 0;
    } else {
      return getResourceName().compareToIgnoreCase(entry.getResourceName());
    }
  }

// --------------------- End Interface Comparable ---------------------

  public Path getActualPath()
  {
    return getActualPath((NearInfinity.getInstance() != null) &&
                         BrowserMenuBar.getInstance().ignoreOverrides());
  }

  public ImageIcon getIcon()
  {
    return ResourceFactory.getKeyfile().getIcon(getExtension());
  }

  public long getResourceSize()
  {
    return getResourceSize((NearInfinity.getInstance() != null) &&
                           BrowserMenuBar.getInstance().ignoreOverrides());
  }

  public ByteBuffer getResourceBuffer() throws Exception
  {
    return getResourceBuffer((NearInfinity.getInstance() != null) &&
                             BrowserMenuBar.getInstance().ignoreOverrides());
  }

  public InputStream getResourceDataAsStream() throws Exception
  {
    return getResourceDataAsStream((NearInfinity.getInstance() != null) &&
                                   BrowserMenuBar.getInstance().ignoreOverrides());
  }

  public int[] getResourceInfo() throws Exception
  {
    return getResourceInfo((NearInfinity.getInstance() != null) &&
                           BrowserMenuBar.getInstance().ignoreOverrides());
  }

  public String getSearchString()
  {
    if (searchString == null) {
      try {
        String extension = getExtension();
        if (extension.equalsIgnoreCase("CRE")) {
          searchString = CreResource.getSearchString(getResourceBuffer());
        } else if (extension.equalsIgnoreCase("ITM")) {
          searchString = ItmResource.getSearchString(getResourceBuffer());
        } else if (extension.equalsIgnoreCase("SPL")) {
          searchString = SplResource.getSearchString(getResourceBuffer());
        } else if (extension.equalsIgnoreCase("STO")) {
          searchString = StoResource.getSearchString(getResourceBuffer());
        }
      } catch (Exception e) {
        if ((NearInfinity.getInstance() != null) &&
            !BrowserMenuBar.getInstance().ignoreReadErrors()) {
          JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Error reading " + toString(),
                                        "Error", JOptionPane.ERROR_MESSAGE);
        }
        searchString = "Error";
        e.printStackTrace();
      }
    }
    return searchString;
  }

  /**
   * Returns whether the current resource matches all of the search options specified in the
   * SearchOptions argument.
   * @param searchOptions Contains the options to check the resource against.
   * @return {@code true} if all options are matching, {@code false} otherwise.
   */
  public boolean matchSearchOptions(SearchOptions searchOptions)
  {
    if (searchOptions != null && !searchOptions.isEmpty()) {
      if ("ARE".equalsIgnoreCase(searchOptions.getResourceType())) {
        return AreResource.matchSearchOptions(this, searchOptions);
      } else if ("CRE".equalsIgnoreCase(searchOptions.getResourceType())) {
        return CreResource.matchSearchOptions(this, searchOptions);
      } else if ("EFF".equalsIgnoreCase(searchOptions.getResourceType())) {
        return EffResource.matchSearchOptions(this, searchOptions);
      } else if ("ITM".equalsIgnoreCase(searchOptions.getResourceType())) {
        return ItmResource.matchSearchOptions(this, searchOptions);
      } else if ("PRO".equalsIgnoreCase(searchOptions.getResourceType())) {
        return ProResource.matchSearchOptions(this, searchOptions);
      } else if ("SPL".equalsIgnoreCase(searchOptions.getResourceType())) {
        return SplResource.matchSearchOptions(this, searchOptions);
      } else if ("STO".equalsIgnoreCase(searchOptions.getResourceType())) {
        return StoResource.matchSearchOptions(this, searchOptions);
      } else if ("VVC".equalsIgnoreCase(searchOptions.getResourceType())) {
        return VvcResource.matchSearchOptions(this, searchOptions);
      }
    }
    return false;
  }

  /**
   * Indicates whether the resource is visible for Near Infinity.
   */
  public boolean isVisible()
  {
    return !skippedExtensions.contains(getExtension().toUpperCase(Locale.ENGLISH));
  }

  protected abstract Path getActualPath(boolean ignoreOverride);

  public abstract long getResourceSize(boolean ignoreOverride);

  public abstract String getExtension();

  public abstract ByteBuffer getResourceBuffer(boolean ignoreOverride) throws Exception;

  public abstract InputStream getResourceDataAsStream(boolean ignoreOverride) throws Exception;

  public abstract int[] getResourceInfo(boolean ignoreOverride) throws Exception;

  public abstract String getResourceName();

  public abstract String getTreeFolder();

  public abstract boolean hasOverride();
}