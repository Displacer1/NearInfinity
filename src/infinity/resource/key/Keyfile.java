// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.resource.key;

import infinity.icon.Icons;
import infinity.resource.ResourceFactory;
import infinity.util.DynamicArray;
import infinity.util.IntegerHashMap;
import infinity.util.io.FileInputStreamNI;
import infinity.util.io.FileOutputStreamNI;
import infinity.util.io.FileReaderNI;
import infinity.util.io.FileWriterNI;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

public final class Keyfile
{
  public static final ImageIcon ICON_STRUCT = Icons.getIcon("RowInsertAfter16.gif");
  private static final ImageIcon ICON_TEXT = Icons.getIcon("Edit16.gif");
  private static final ImageIcon ICON_UNKNOWN = Icons.getIcon("Help16.gif");
  private static final ImageIcon ICON_SOUND = Icons.getIcon("Volume16.gif");
  private static final ImageIcon ICON_MOVIE = Icons.getIcon("Movie16.gif");
  private static final ImageIcon ICON_SCRIPT = Icons.getIcon("History16.gif");
  private static final ImageIcon ICON_IMAGE = Icons.getIcon("Color16.gif");
  private static final ImageIcon ICON_BUNDLE = Icons.getIcon("Bundle16.gif");
  private final File keyfile;
  private final IntegerHashMap<String> extmap = new IntegerHashMap<String>();
  private final Map<String, ImageIcon> resourceicons = new HashMap<String, ImageIcon>();
  private BIFFArchive currentBIFF; // Caching of last BifFile - improves performance
  private BIFFEntry currentBIFFEntry;
  private List<BIFFEntry> biffEntries;
  private String signature, version;

  public Keyfile(File keyfile)
  {
    this.keyfile = keyfile;
    resourceicons.clear();
    resourceicons.put("???", ICON_UNKNOWN);
    extmap.put(0x001, "BMP");
    resourceicons.put("BMP", ICON_IMAGE);
    extmap.put(0x002, "MVE");
    resourceicons.put("MVE", ICON_MOVIE);
    extmap.put(0x004, "WAV");
    resourceicons.put("WAV", ICON_SOUND);
    extmap.put(0x005, "WFX");
    resourceicons.put("WFX", ICON_STRUCT);
    extmap.put(0x006, "PLT");
    resourceicons.put("PLT", ICON_IMAGE);
    extmap.put(0x3e8, "BAM");
    resourceicons.put("BAM", ICON_MOVIE);
    extmap.put(0x3e9, "WED");
    resourceicons.put("WED", ICON_STRUCT);
    extmap.put(0x3ea, "CHU");
    resourceicons.put("CHU", ICON_STRUCT);
    extmap.put(0x3eb, "TIS");
    resourceicons.put("TIS", ICON_IMAGE);
    extmap.put(0x3ec, "MOS");
    resourceicons.put("MOS", ICON_IMAGE);
    extmap.put(0x3ed, "ITM");
    resourceicons.put("ITM", ICON_STRUCT);
    extmap.put(0x3ee, "SPL");
    resourceicons.put("SPL", ICON_STRUCT);
    extmap.put(0x3ef, "BCS");
    resourceicons.put("BCS", ICON_SCRIPT);
    extmap.put(0x3f0, "IDS");
    resourceicons.put("IDS", ICON_TEXT);
    extmap.put(0x3f1, "CRE");
    resourceicons.put("CRE", ICON_STRUCT);
    extmap.put(0x3f2, "ARE");
    resourceicons.put("ARE", ICON_STRUCT);
    extmap.put(0x3f3, "DLG");
    resourceicons.put("DLG", ICON_STRUCT);
    extmap.put(0x3f4, "2DA");
    resourceicons.put("2DA", ICON_TEXT);
    extmap.put(0x3f5, "GAM");
    resourceicons.put("GAM", ICON_STRUCT);
    extmap.put(0x3f6, "STO");
    resourceicons.put("STO", ICON_STRUCT);
    extmap.put(0x3f7, "WMP");
    resourceicons.put("WMP", ICON_STRUCT);
    extmap.put(0x3f8, "EFF");
    resourceicons.put("EFF", ICON_STRUCT);
    extmap.put(0x3f9, "BS");
    resourceicons.put("BS", ICON_SCRIPT);
    extmap.put(0x3fa, "CHR");
    resourceicons.put("CHR", ICON_STRUCT);
    extmap.put(0x3fb, "VVC");
    resourceicons.put("VVC", ICON_STRUCT);
    extmap.put(0x3fc, "VEF");
    resourceicons.put("VEF", ICON_STRUCT);
    extmap.put(0x3fd, "PRO");
    resourceicons.put("PRO", ICON_STRUCT);
    extmap.put(0x3fe, "BIO");
    resourceicons.put("BIO", ICON_TEXT);
    extmap.put(0x3ff, "WBM");
    resourceicons.put("WBM", ICON_MOVIE);
    extmap.put(0x44c, "BAH"); // ???????
    extmap.put(0x802, "INI");
    resourceicons.put("INI", ICON_TEXT);
    extmap.put(0x803, "SRC");
    resourceicons.put("SRC", ICON_STRUCT);
    extmap.put(0x400, "FNT");
    resourceicons.put("FNT", ICON_IMAGE);
    extmap.put(0x401, "WBM");
    resourceicons.put("WBM", ICON_MOVIE);
    extmap.put(0x402, "GUI");
    resourceicons.put("GUI", ICON_TEXT);
    extmap.put(0x403, "SQL");
    resourceicons.put("SQL", ICON_TEXT);
    extmap.put(0x404, "PVRZ");
    resourceicons.put("PVRZ", ICON_IMAGE);
    extmap.put(0x405, "GLSL");
    resourceicons.put("GLSL", ICON_TEXT);

    resourceicons.put("ACM", ICON_SOUND);
    resourceicons.put("MUS", ICON_SOUND);
    resourceicons.put("SAV", ICON_BUNDLE);
    resourceicons.put("TXT", ICON_TEXT);
    resourceicons.put("RES", ICON_TEXT);
    resourceicons.put("BAF", ICON_SCRIPT);
    resourceicons.put("TOH", ICON_STRUCT);
    resourceicons.put("TOT", ICON_STRUCT);
  }

  @Override
  public boolean equals(Object o)
  {
    if (!(o instanceof Keyfile))
      return false;
    Keyfile other = (Keyfile)o;
    return signature.equals(other.signature) && version.equals(other.version);
  }

  @Override
  public String toString()
  {
    return keyfile.toString();
  }

  public void addBIFFEntry(BIFFEntry entry)
  {
    biffEntries.add(entry);
    entry.setIndex(biffEntries.size() - 1);
  }

  public void addBIFFResourceEntries(ResourceTreeModel treemodel) throws Exception
  {
    BufferedInputStream is = new BufferedInputStream(new FileInputStreamNI(keyfile));
    byte buffer[] = FileReaderNI.readBytes(is, (int)keyfile.length());
    is.close();

    signature = new String(buffer, 0, 4);
    version = new String(buffer, 4, 4);
    if (!signature.equals("KEY ") || !version.equals("V1  ")) {
      JOptionPane.showMessageDialog(null, "Unsupported keyfile: " + keyfile, "Error",
                                    JOptionPane.ERROR_MESSAGE);
      throw new IOException();
    }
    int numbif = DynamicArray.getInt(buffer, 8);
    int numres = DynamicArray.getInt(buffer, 12);
    int bifoff = DynamicArray.getInt(buffer, 16);
    int resoff = DynamicArray.getInt(buffer, 20);

    biffEntries = new ArrayList<BIFFEntry>(numbif);
    for (int i = 0; i < numbif; i++)
      biffEntries.add(new BIFFEntry(i, buffer, bifoff + 12 * i));

    for (int i = 0; i < numres; i++) {
      BIFFResourceEntry entry = new BIFFResourceEntry(buffer, resoff + 14 * i, 8);
      treemodel.addResourceEntry(entry, entry.getExtension());
    }
  }

  public boolean cleanUp()
  {
    closeBIFFFile();
    Set<BIFFEntry> toRemove = new HashSet<BIFFEntry>(biffEntries);
    // Determine BIFFs with no files in them
    List<BIFFResourceEntry> resourceEntries =
            ResourceFactory.getResources().getBIFFResourceEntries();
    for (int i = 0; i < resourceEntries.size(); i++) {
      BIFFResourceEntry entry = resourceEntries.get(i);
      toRemove.remove(entry.getBIFFEntry());
    }
    // Delete these BIFFs
    for (final BIFFEntry entry : toRemove) {
      File file = entry.getFile();
      System.out.println("Deleting " + file);
      if (file != null)
        file.delete();
    }
    // Determine non-existant BIFFs
    for (int i = 0; i < biffEntries.size(); i++) {
      BIFFEntry entry = biffEntries.get(i);
      if (entry.getFile() == null)
        toRemove.add(entry);
    }
    if (toRemove.isEmpty())
      return false;
    // Remove bugus BIFFs from keyfile
    for (final BIFFEntry entry : toRemove)
      removeBIFFEntry(entry);
    return true;
  }

  public void closeBIFFFile()
  {
    if (currentBIFF != null)
      try {
        currentBIFF.close();
        currentBIFFEntry = null;
      } catch (IOException e) {
        e.printStackTrace();
      }
  }

  public Object[] getBIFFEntriesSorted()
  {
    List<BIFFEntry> list = new ArrayList<BIFFEntry>(biffEntries);
    Collections.sort(list);
    return list.toArray();
  }

  public BIFFEntry getBIFFEntry(int index)
  {
    return biffEntries.get(index);
  }

  public BIFFArchive getBIFFFile(BIFFEntry entry) throws IOException
  {
    if (currentBIFFEntry == entry)
      return currentBIFF; // Caching
    File file = entry.getFile();
    if (file == null)
      throw new IOException(entry + " not found");
    if (currentBIFF != null)
      currentBIFF.close();
    currentBIFFEntry = entry;
    currentBIFF = new BIFFArchive(file);
    return currentBIFF;
  }

  public String getExtension(int type)
  {
    return extmap.get(type);
  }

  public int getExtensionType(String extension)
  {
    int[] keys = extmap.keys();
    for (int type : keys) {
      if (extmap.get(type).equalsIgnoreCase(extension))
        return type;
    }
    return -1;
  }

  public ImageIcon getIcon(String extension)
  {
    ImageIcon icon = resourceicons.get(extension);
    if (icon == null)
      return resourceicons.get("???");
    return icon;
  }

  public void write() throws IOException
  {
    BufferedOutputStream os = new BufferedOutputStream(new FileOutputStreamNI(keyfile));
    int bifoff = 0x18;
    int offset = bifoff + 0x0c * biffEntries.size();
    for (int i = 0; i < biffEntries.size(); i++)
      offset += biffEntries.get(i).updateOffset(offset);
    int resoff = offset;

    List<BIFFResourceEntry> resourceentries = ResourceFactory.getResources().getBIFFResourceEntries();

    FileWriterNI.writeString(os, signature, 4);
    FileWriterNI.writeString(os, version, 4);
    FileWriterNI.writeInt(os, biffEntries.size());
    FileWriterNI.writeInt(os, resourceentries.size());
    FileWriterNI.writeInt(os, bifoff);
    FileWriterNI.writeInt(os, resoff);

    for (int i = 0; i < biffEntries.size(); i++)
      biffEntries.get(i).write(os);
    for (int i = 0; i < biffEntries.size(); i++)
      biffEntries.get(i).writeString(os);

    for (int i = 0; i < resourceentries.size(); i++)
      resourceentries.get(i).write(os);
    os.close();
  }

  private void removeBIFFEntry(BIFFEntry entry)
  {
    System.out.println("Removing " + entry);
    int index = biffEntries.indexOf(entry);
    // Remove bogus BIFFResourceEntries
    ResourceTreeModel resources = ResourceFactory.getResources();
    for (final BIFFResourceEntry resourceEntry : resources.getBIFFResourceEntries()) {
      if (resourceEntry.getBIFFEntry() == entry)
        resources.removeResourceEntry(resourceEntry);
      else
        resourceEntry.adjustSourceIndex(index);     // Update relevant BIFFResourceEntries
    }
    // Remove BIFFEntry
    biffEntries.remove(entry);
    // Update relevant BIFFEntries
    for (int i = index; i < biffEntries.size(); i++) {
      BIFFEntry e = biffEntries.get(i);
      e.setIndex(i);
    }
  }
}

