// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.infinity.gui.StructViewer;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.graphics.GraphicsResource;

public final class ColorValue extends Datatype implements Editable, IsNumeric, ChangeListener, ActionListener
{
  private static BufferedImage image;
  private JLabel colors[], infolabel;
  private JSlider slider;
  private JTextField tfield;
  private int shownnumber;
  private int number;

  private static Color getColor(int index, int brightness)
  {
    if (image == null)
      initImage();
    if (index >= image.getHeight() || brightness >= image.getWidth())
      return null;
    return new Color(image.getRGB(brightness, index));
  }

  private static int getNumColors()
  {
    if (image == null)
      initImage();
    return image.getHeight();
  }

  private static int getRangeSize()
  {
    if (image == null)
      initImage();
    return image.getWidth();
  }

  private static void initImage()
  {
    try {
      if (ResourceFactory.resourceExists("RANGES12.BMP"))
        image = new GraphicsResource(ResourceFactory.getResourceEntry("RANGES12.BMP")).getImage();
      else
        image = new GraphicsResource(ResourceFactory.getResourceEntry("MPALETTE.BMP")).getImage();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public ColorValue(ByteBuffer buffer, int offset, int length, String name)
  {
    this(null, buffer, offset, length, name);
  }

  public ColorValue(StructEntry parent, ByteBuffer buffer, int offset, int length, String name)
  {
    super(parent, offset, length, name);
    read(buffer, offset);
  }

// --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (event.getSource() == tfield) {
      try {
        int newnumber = Integer.parseInt(tfield.getText());
        if (newnumber < (1L << (8*getSize()))) {
          shownnumber = newnumber;
        } else {
          tfield.setText(String.valueOf(shownnumber));
        }
      } catch (NumberFormatException e) {
        tfield.setText(String.valueOf(shownnumber));
      }
      slider.setValue(shownnumber);
      setColors();
    }
  }

// --------------------- End Interface ActionListener ---------------------


// --------------------- Begin Interface ChangeListener ---------------------

  @Override
  public void stateChanged(ChangeEvent event)
  {
    if (event.getSource() == slider) {
      tfield.setText(String.valueOf(slider.getValue()));
      shownnumber = slider.getValue();
      setColors();
    }
  }

// --------------------- End Interface ChangeListener ---------------------


// --------------------- Begin Interface Editable ---------------------

  @Override
  public JComponent edit(ActionListener container)
  {
    if (tfield == null) {
      tfield = new JTextField(4);
      tfield.setHorizontalAlignment(JTextField.CENTER);
      colors = new JLabel[getRangeSize()];
      for (int i = 0; i < colors.length; i++) {
        colors[i] = new JLabel("     ");
        colors[i].setOpaque(true);
      }
      tfield.addActionListener(this);
      slider = new JSlider(0, Math.max(number, getNumColors() - 1), number);
      slider.addChangeListener(this);
      slider.setMajorTickSpacing(25);
      slider.setMinorTickSpacing(5);
      slider.setPaintTicks(true);
      infolabel = new JLabel(" ", JLabel.CENTER);
    }
    tfield.setText(String.valueOf(number));
    shownnumber = number;
    setColors();

    JButton bUpdate = new JButton("Update value", Icons.getIcon(Icons.ICON_REFRESH_16));
    bUpdate.addActionListener(container);
    bUpdate.setActionCommand(StructViewer.UPDATE_VALUE);

    JLabel label = new JLabel(getName() + ": ");

    JPanel cpanel = new JPanel();
    cpanel.setLayout(new GridLayout(1, colors.length));
    for (final JLabel color : colors)
      cpanel.add(color);

    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    JPanel panel = new JPanel(gbl);

    gbc.insets = new Insets(3, 0, 3, 3);
    gbl.setConstraints(label, gbc);
    panel.add(label);

    gbl.setConstraints(tfield, gbc);
    panel.add(tfield);

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbl.setConstraints(slider, gbc);
    panel.add(slider);

    gbl.setConstraints(cpanel, gbc);
    panel.add(cpanel);

    gbl.setConstraints(infolabel, gbc);
    panel.add(infolabel);

    gbl.setConstraints(bUpdate, gbc);
    panel.add(bUpdate);

    return panel;
  }

  @Override
  public void select()
  {
  }

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    try {
      int newnumber = Integer.parseInt(tfield.getText());
      if (newnumber >= (1L << (8*getSize()))) {
        return false;
      }
      number = newnumber;
      shownnumber = number;
      setColors();

      // notifying listeners
      fireValueUpdated(new UpdateEvent(this, struct));

      return true;
    } catch (NumberFormatException e) {
      e.printStackTrace();
    }
    return false;
  }

// --------------------- End Interface Editable ---------------------


// --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    writeInt(os, number);
  }

// --------------------- End Interface Writeable ---------------------

//--------------------- Begin Interface Readable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    buffer.position(offset);
    switch (getSize()) {
      case 1:
        number = buffer.get();
        break;
      case 2:
        number = buffer.getShort();
        break;
      case 4:
        number = buffer.getInt();
        break;
      default:
        throw new IllegalArgumentException();
    }

    return offset + getSize();
  }

//--------------------- End Interface Readable ---------------------

//--------------------- Begin Interface IsNumeric ---------------------

  @Override
  public long getLongValue()
  {
    return (long)number & 0xffffffffL;
  }

  @Override
  public int getValue()
  {
    return number;
  }

//--------------------- End Interface IsNumeric ---------------------

  @Override
  public String toString()
  {
    return "Color index " + number;
  }

  private void setColors()
  {
    for (int i = 0; i < colors.length; i++) {
      Color c = getColor(shownnumber, i);
      if (c != null) {
        colors[i].setText("     ");
        colors[i].setBackground(c);
      }
      else {
        colors[i].setText(" ? ");
        colors[i].setBackground(Color.white);
      }
      colors[i].repaint();
    }
    if (shownnumber > 199 && ResourceFactory.resourceExists("RANDCOLR.2DA"))
      infolabel.setText("Color drawn from RANDCOLR.2DA");
    else
      infolabel.setText("");
  }
}

