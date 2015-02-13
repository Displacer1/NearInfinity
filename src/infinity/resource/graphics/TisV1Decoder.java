// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.resource.graphics;

import infinity.resource.key.ResourceEntry;
import infinity.util.DynamicArray;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Handles legacy TIS resources (using palettized tiles).
 * @author argent77
 */
public class TisV1Decoder extends TisDecoder
{
  private static final int HeaderSize = 24;   // Size of the TIS header
  private static final Color TransparentColor = new Color(0, true);

  private byte[] tisData;
  private int tileCount, tileSize;
  private int[] workingPalette;
  private BufferedImage workingCanvas;

  public TisV1Decoder(ResourceEntry tisEntry)
  {
    super(tisEntry);
    init();
  }

  /**
   * Returns the palette of the specified tile.
   * @param tileIdx The tile index
   * @return The palette as int array of 256 entries (Format: ARGB).
   *         Returns <code>null</code> on error.
   */
  public int[] getTilePalette(int tileIdx)
  {
    if (tileIdx >= 0 && tileIdx < getTileCount()) {
      int[] palette = new int[256];
      getTilePalette(tileIdx, palette);
      return palette;
    } else {
      return null;
    }
  }

  /**
   * Writes the palette entries of the specified tile into the buffer.
   * @param tileIdx The tile index
   * @param buffer The buffer to write the palette data into.
   */
  public void getTilePalette(int tileIdx, int[] buffer)
  {
    if (buffer != null) {
      int ofs = getTileOffset(tileIdx);
      if (ofs > 0) {
        int maxLen = (buffer.length > 256) ? 256 : buffer.length;
        for (int i = 0; i < maxLen; i++) {
          buffer[i] = DynamicArray.getInt(tisData, ofs);
          if (i == 0 && (buffer[i] & 0x00ffffff) == 0x0000ff00) {
            buffer[i] &= 0xff000000;
          } else {
            buffer[i] |= 0xff000000;
          }
          ofs += 4;
        }
      }
    }
  }

  /**
   * Returns the unprocessed tile data (without palette).
   * @param tileIdx The tile index
   * @return Unprocessed data of the specified tile without the palette block.
   *         Returns <code>null</code> on error.
   */
  public byte[] getRawTileData(int tileIdx)
  {
    if (tileIdx >= 0 && tileIdx < getTileCount()) {
      byte[] buffer = new byte[TileDimension*TileDimension];
      getRawTileData(tileIdx, buffer);
      return buffer;
    } else {
      return null;
    }
  }

  /**
   * Writes unprocessed tile data into the specified buffer (without palette data).
   * @param tileIdx The tile index
   * @param buffer The buffer to write the tile data into.
   */
  public void getRawTileData(int tileIdx, byte[] buffer)
  {
    if (buffer != null) {
      int ofs = getTileOffset(tileIdx);
      if (ofs > 0) {
        int maxSize = (buffer.length > TileDimension*TileDimension) ? TileDimension*TileDimension : buffer.length;
        System.arraycopy(tisData, ofs, buffer, 0, maxSize);
      }
    }
  }


  @Override
  public void close()
  {
    tisData = null;
    tileCount = 0;
    tileSize = 0;
    workingPalette = null;
    if (workingCanvas != null) {
      workingCanvas.flush();
      workingCanvas = null;
    }
  }

  @Override
  public void reload()
  {
    init();
  }

  @Override
  public byte[] getResourceData()
  {
    return tisData;
  }

  @Override
  public int getTileWidth()
  {
    return TileDimension;
  }

  @Override
  public int getTileHeight()
  {
    return TileDimension;
  }

  @Override
  public int getTileCount()
  {
    return tileCount;
  }

  @Override
  public Image getTile(int tileIdx)
  {
    BufferedImage image = ColorConvert.createCompatibleImage(TileDimension, TileDimension, true);
    renderTile(tileIdx, image);
    return image;
  }

  @Override
  public boolean getTile(int tileIdx, Image canvas)
  {
    return renderTile(tileIdx, canvas);
  }

  @Override
  public int[] getTileData(int tileIdx)
  {
    int[] buffer = new int[TileDimension*TileDimension];
    renderTile(tileIdx, buffer);
    return buffer;
  }

  @Override
  public boolean getTileData(int tileIdx, int[] buffer)
  {
    return renderTile(tileIdx, buffer);
  }


  private void init()
  {
    close();

    if (getResourceEntry() != null) {
      try {
        int[] info = getResourceEntry().getResourceInfo();
        if (info == null || info.length < 2) {
          throw new Exception("Error reading TIS header");
        }

        tileCount = info[0];
        if (tileCount <= 0) {
          throw new Exception("Invalid tile count: " + tileCount);
        }
        tileSize = info[1];
        if (tileSize != 1024 + TileDimension*TileDimension) {
          throw new Exception("Invalid tile size: " + tileSize);
        }
        tisData = getResourceEntry().getResourceData();

        setType(Type.PALETTE);

        workingPalette = new int[256];
        workingCanvas = new BufferedImage(TileDimension, TileDimension, BufferedImage.BITMASK);
      } catch (Exception e) {
        e.printStackTrace();
        close();
      }
    }
  }

  // Returns the start offset of the specified tile. Returns -1 on error.
  private int getTileOffset(int tileIdx)
  {
    if (tileIdx >= 0 && tileIdx < getTileCount()) {
      return HeaderSize + tileIdx*tileSize;
    } else {
      return -1;
    }
  }

  // Paints the specified tile onto the canvas
  private boolean renderTile(int tileIdx, Image canvas)
  {
    if (canvas != null && canvas.getWidth(null) >= TileDimension && canvas.getHeight(null) >= TileDimension) {
      int[] buffer = ((DataBufferInt)workingCanvas.getRaster().getDataBuffer()).getData();
      if (renderTile(tileIdx, buffer)) {
        buffer = null;
        Graphics2D g = (Graphics2D)canvas.getGraphics();
        try {
          g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
          g.setColor(TransparentColor);
          g.fillRect(0, 0, TileDimension, TileDimension);
          g.drawImage(workingCanvas, 0, 0, null);
        } finally {
          g.dispose();
          g = null;
        }
        return true;
      }
      buffer = null;
    }
    return false;
  }

  // Writes the specified tile data into the buffer
  private boolean renderTile(int tileIdx, int[] buffer)
  {
    int size = TileDimension*TileDimension;
    if (buffer != null && buffer.length >= size) {
      int ofs = getTileOffset(tileIdx);
      if (ofs > 0) {
        ofs += 1024;    // skipping palette data
        getTilePalette(tileIdx, workingPalette);
        for (int i = 0; i < size; i++, ofs++) {
          buffer[i] = workingPalette[tisData[ofs] & 0xff];
        }
        return true;
      }
    }
    return false;
  }
}
