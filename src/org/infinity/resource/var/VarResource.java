// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.var;

import javax.swing.JComponent;

import org.infinity.gui.StructViewer;
import org.infinity.gui.hexview.BasicColorMap;
import org.infinity.gui.hexview.StructHexViewer;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.resource.HasAddRemovable;
import org.infinity.resource.HasViewerTabs;
import org.infinity.resource.Resource;
import org.infinity.resource.key.ResourceEntry;

public final class VarResource extends AbstractStruct implements Resource, HasAddRemovable, HasViewerTabs
{
  private StructHexViewer hexViewer;

  public VarResource(ResourceEntry entry) throws Exception
  {
    super(entry);
  }

// --------------------- Begin Interface HasAddRemovable ---------------------

  @Override
  public AddRemovable[] getAddRemovables() throws Exception
  {
    return new AddRemovable[]{new Entry()};
  }

  @Override
  public AddRemovable confirmAddEntry(AddRemovable entry) throws Exception
  {
    return entry;
  }

  @Override
  public boolean confirmRemoveEntry(AddRemovable entry) throws Exception
  {
    return true;
  }

// --------------------- End Interface HasAddRemovable ---------------------

//--------------------- Begin Interface HasViewerTabs ---------------------

  @Override
  public int getViewerTabCount()
  {
    return 1;
  }

  @Override
  public String getViewerTabName(int index)
  {
    return StructViewer.TAB_RAW;
  }

  @Override
  public JComponent getViewerTab(int index)
  {
    if (hexViewer == null) {
      BasicColorMap colorMap = new BasicColorMap(this, false);
      colorMap.setColoredEntry(BasicColorMap.Coloring.BLUE, Entry.class);
      hexViewer = new StructHexViewer(this, colorMap);
    }
    return hexViewer;
  }

  @Override
  public boolean viewerTabAddedBefore(int index)
  {
    return false;
  }

//--------------------- End Interface HasViewerTabs ---------------------

  @Override
  public int read(byte buffer[], int offset) throws Exception
  {
    int count = buffer.length / 44;
    for (int i = 0; i < count; i++)
      addField(new Entry(this, buffer, offset + i * 44, i));
    return offset + count * 44;
  }

  @Override
  protected void viewerInitialized(StructViewer viewer)
  {
    viewer.addTabChangeListener(hexViewer);
  }

  @Override
  protected void datatypeAdded(AddRemovable datatype)
  {
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeAddedInChild(AbstractStruct child, AddRemovable datatype)
  {
    super.datatypeAddedInChild(child, datatype);
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemoved(AddRemovable datatype)
  {
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }

  @Override
  protected void datatypeRemovedInChild(AbstractStruct child, AddRemovable datatype)
  {
    super.datatypeRemovedInChild(child, datatype);
    if (hexViewer != null) {
      hexViewer.dataModified();
    }
  }
}
