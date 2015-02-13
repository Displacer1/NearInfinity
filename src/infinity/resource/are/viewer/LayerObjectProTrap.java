// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.resource.are.viewer;

import java.awt.Image;
import java.awt.Point;

import infinity.datatype.DecNumber;
import infinity.datatype.ResourceRef;
import infinity.gui.layeritem.AbstractLayerItem;
import infinity.gui.layeritem.IconLayerItem;
import infinity.icon.Icons;
import infinity.resource.AbstractStruct;
import infinity.resource.are.AreResource;
import infinity.resource.are.ProTrap;
import infinity.resource.are.viewer.icon.ViewerIcons;

/**
 * Handles specific layer type: ARE/Projectile Trap
 * @author argent77
 */
public class LayerObjectProTrap extends LayerObject
{
  private static final Image[] Icon = new Image[]{Icons.getImage(ViewerIcons.class, "itm_ProTrap1.png"),
                                                  Icons.getImage(ViewerIcons.class, "itm_ProTrap2.png")};
  private static Point Center = new Point(14, 14);

  private final ProTrap trap;
  private final Point location = new Point();

  private IconLayerItem item;

  public LayerObjectProTrap(AreResource parent, ProTrap trap)
  {
    super(ViewerConstants.RESOURCE_ARE, "Trap", ProTrap.class, parent);
    this.trap = trap;
    init();
  }

  @Override
  public AbstractStruct getStructure()
  {
    return trap;
  }

  @Override
  public AbstractStruct[] getStructures()
  {
    return new AbstractStruct[]{trap};
  }

  @Override
  public AbstractLayerItem getLayerItem()
  {
    return item;
  }

  @Override
  public AbstractLayerItem getLayerItem(int type)
  {
    return (type == 0) ? item : null;
  }

  @Override
  public AbstractLayerItem[] getLayerItems()
  {
    return new AbstractLayerItem[]{item};
  }

  @Override
  public void reload()
  {
    init();
  }

  @Override
  public void update(double zoomFactor)
  {
    if (item != null) {
      item.setItemLocation((int)(location.x*zoomFactor + (zoomFactor / 2.0)),
                           (int)(location.y*zoomFactor + (zoomFactor / 2.0)));
    }
  }

  @Override
  public Point getMapLocation()
  {
    return location;
  }

  @Override
  public Point[] getMapLocations()
  {
    return new Point[]{location};
  }

  private void init()
  {
    if (trap != null) {
      String msg = "";
      try {
        location.x = ((DecNumber)trap.getAttribute("Location: X")).getValue();
        location.y = ((DecNumber)trap.getAttribute("Location: Y")).getValue();
        msg = ((ResourceRef)trap.getAttribute("Trap")).toString();
        int target = ((DecNumber)trap.getAttribute("Target")).getValue();
        if (target < 0) target = 0; else if (target > 255) target = 255;
        if (target >= 2 && target <= 30) {
          msg += " (hostile)";
        } else if (target >= 200) {
          msg += " (friendly)";
        }
      } catch (Exception e) {
        e.printStackTrace();
      }

      // Using cached icons
      Image[] icon;
      String keyIcon = String.format("%1$s%2$s", SharedResourceCache.createKey(Icon[0]),
                                                 SharedResourceCache.createKey(Icon[1]));
      if (SharedResourceCache.contains(SharedResourceCache.Type.Icon, keyIcon)) {
        icon = ((ResourceIcon)SharedResourceCache.get(SharedResourceCache.Type.Icon, keyIcon)).getData();
        SharedResourceCache.add(SharedResourceCache.Type.Icon, keyIcon);
      } else {
        icon = Icon;
        SharedResourceCache.add(SharedResourceCache.Type.Icon, keyIcon, new ResourceIcon(keyIcon, icon));
      }

      item = new IconLayerItem(location, trap, msg, icon[0], Center);
      item.setName(getCategory());
      item.setToolTipText(msg);
      item.setImage(AbstractLayerItem.ItemState.HIGHLIGHTED, icon[1]);
      item.setVisible(isVisible());
    }
  }
}
