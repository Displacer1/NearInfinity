// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.graphics;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

import org.infinity.gui.ButtonPanel;
import org.infinity.gui.ButtonPopupMenu;
import org.infinity.gui.RenderCanvas;
import org.infinity.gui.WindowBlocker;
import org.infinity.resource.Closeable;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.ViewableContainer;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.search.ReferenceSearcher;
import org.infinity.util.io.StreamUtils;

public class PvrzResource implements Resource, ActionListener, Closeable
{
  private final ResourceEntry entry;
  private final ButtonPanel buttonPanel = new ButtonPanel();

  private JMenuItem miExport, miPNG;
  private RenderCanvas rcImage;
  private JPanel panel;

  public PvrzResource(ResourceEntry entry) throws Exception
  {
    this.entry = entry;
  }

//--------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (buttonPanel.getControlByType(ButtonPanel.Control.FIND_REFERENCES) == event.getSource()) {
      new ReferenceSearcher(entry, new String[]{"BAM", "MOS", "TIS"}, panel.getTopLevelAncestor());
    }
    else if (event.getSource() == miExport) {
      // export as original PVRZ
      ResourceFactory.exportResource(entry, panel.getTopLevelAncestor());
    } else if (event.getSource() == miPNG) {
      try {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String fileName = entry.toString().replace(".PVRZ", ".PNG");
        BufferedImage image = getImage();
        if (ImageIO.write(image, "png", os)) {
          ResourceFactory.exportResource(entry, StreamUtils.getByteBuffer(os.toByteArray()),
                                         fileName, panel.getTopLevelAncestor());
        } else {
          JOptionPane.showMessageDialog(panel.getTopLevelAncestor(),
                                        "Error while exporting " + entry, "Error",
                                        JOptionPane.ERROR_MESSAGE);
        }
        os.close();
        os = null;
        image = null;
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

//--------------------- End Interface ActionListener ---------------------

//--------------------- Begin Interface Resource ---------------------

  @Override
  public ResourceEntry getResourceEntry()
  {
    return entry;
  }

//--------------------- End Interface Resource ---------------------

//--------------------- Begin Interface Closeable ---------------------

  @Override
  public void close() throws Exception
  {
    panel.removeAll();
    rcImage.setImage(null);
    rcImage = null;
  }

//--------------------- End Interface Closeable ---------------------

//--------------------- Begin Interface Viewable ---------------------

  @Override
  public JComponent makeViewer(ViewableContainer container)
  {
    JButton btn = ((JButton)buttonPanel.addControl(ButtonPanel.Control.FIND_REFERENCES));
    btn.addActionListener(this);
    btn.setEnabled(Profile.isEnhancedEdition());

    miExport = new JMenuItem("original");
    miExport.addActionListener(this);
    miPNG = new JMenuItem("as PNG");
    miPNG.addActionListener(this);
    ButtonPopupMenu bpmExport = (ButtonPopupMenu)buttonPanel.addControl(ButtonPanel.Control.EXPORT_MENU);
    bpmExport.setMenuItems(new JMenuItem[]{miExport, miPNG});

    rcImage = new RenderCanvas();
    rcImage.setHorizontalAlignment(SwingConstants.CENTER);
    rcImage.setVerticalAlignment(SwingConstants.CENTER);
    WindowBlocker.blockWindow(true);
    try {
      rcImage.setImage(loadImage());
      WindowBlocker.blockWindow(false);
    } catch (Exception e) {
      WindowBlocker.blockWindow(false);
    }
    JScrollPane scroll = new JScrollPane(rcImage);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    scroll.getHorizontalScrollBar().setUnitIncrement(16);

    panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(scroll, BorderLayout.CENTER);
    panel.add(buttonPanel, BorderLayout.SOUTH);
    scroll.setBorder(BorderFactory.createLoweredBevelBorder());

    return panel;
  }

//--------------------- End Interface Viewable ---------------------

  public BufferedImage getImage()
  {
    if (rcImage != null) {
      return ColorConvert.toBufferedImage(rcImage.getImage(), false);
    } else if (entry != null) {
      return loadImage();
    }
    return null;
  }

  private BufferedImage loadImage()
  {
    BufferedImage image = null;
    PvrDecoder decoder = null;
    if (entry != null) {
      try {
        decoder = PvrDecoder.loadPvr(entry);
        image = new BufferedImage(decoder.getWidth(), decoder.getHeight(), BufferedImage.TYPE_INT_ARGB);
        if (!decoder.decode(image)) {
          image = null;
        }
        decoder = null;
      } catch (Exception e) {
        image = null;
        if (decoder != null) {
          decoder = null;
        }
        e.printStackTrace();
      }
    }
    return image;
  }

}
