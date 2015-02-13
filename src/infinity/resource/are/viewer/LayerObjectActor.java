// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.resource.are.viewer;

import java.awt.Image;
import java.awt.Point;

import infinity.datatype.DecNumber;
import infinity.datatype.Flag;
import infinity.datatype.IdsBitmap;
import infinity.datatype.ResourceRef;
import infinity.datatype.StringRef;
import infinity.datatype.TextString;
import infinity.gui.layeritem.AbstractLayerItem;
import infinity.gui.layeritem.IconLayerItem;
import infinity.icon.Icons;
import infinity.resource.AbstractStruct;
import infinity.resource.ResourceFactory;
import infinity.resource.StructEntry;
import infinity.resource.are.Actor;
import infinity.resource.are.AreResource;
import infinity.resource.are.viewer.icon.ViewerIcons;
import infinity.resource.cre.CreResource;

/**
 * Handles specific layer type: ARE/Actor
 * @author argent77
 */
public class LayerObjectActor extends LayerObject
{
  private static final Image[] IconGood = new Image[]{Icons.getImage(ViewerIcons.class, "itm_ActorG1.png"),
                                                      Icons.getImage(ViewerIcons.class, "itm_ActorG2.png")};
  private static final Image[] IconNeutral = new Image[]{Icons.getImage(ViewerIcons.class, "itm_ActorB1.png"),
                                                         Icons.getImage(ViewerIcons.class, "itm_ActorB2.png")};
  private static final Image[] IconEvil = new Image[]{Icons.getImage(ViewerIcons.class, "itm_ActorR1.png"),
                                                      Icons.getImage(ViewerIcons.class, "itm_ActorR2.png")};
  private static final Point Center = new Point(12, 40);

  private final Actor actor;
  private final Point location = new Point();

  private IconLayerItem item;
  private Flag scheduleFlags;


  public LayerObjectActor(AreResource parent, Actor actor)
  {
    super(ViewerConstants.RESOURCE_ARE, "Actor", Actor.class, parent);
    this.actor = actor;
    init();
  }

  @Override
  public void close()
  {
    // TODO: implement method
    super.close();
  }

  @Override
  public AbstractStruct getStructure()
  {
    return actor;
  }

  @Override
  public AbstractStruct[] getStructures()
  {
    return new AbstractStruct[]{actor};
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

  @Override
  public boolean isScheduled(int schedule)
  {
    if (schedule >= ViewerConstants.TIME_0 && schedule <= ViewerConstants.TIME_23) {
      return (scheduleFlags.isFlagSet(schedule));
    } else {
      return false;
    }
  }


  private void init()
  {
    if (actor != null) {
      String msg = "";
      Image[] icon = IconNeutral;
      int ea = 128;   // default: neutral
      try {
        location.x = ((DecNumber)actor.getAttribute("Position: X")).getValue();
        location.y = ((DecNumber)actor.getAttribute("Position: Y")).getValue();

        scheduleFlags = ((Flag)actor.getAttribute("Present at"));

        StructEntry obj = actor.getAttribute("Character");
        CreResource cre = null;
        if (obj instanceof TextString) {
          // ARE in saved game
          cre = (CreResource)actor.getAttribute("CRE file");
        } else if (obj instanceof ResourceRef) {
          String creName = ((ResourceRef)obj).getResourceName();
          if (creName.lastIndexOf('.') > 0) {
            cre = new CreResource(ResourceFactory.getResourceEntry(creName));
          }
        }
        if (cre != null) {
          msg = ((StringRef)cre.getAttribute("Name")).toString();
          ea = (int)((IdsBitmap)cre.getAttribute("Allegiance")).getValue();
        }
        if (ea >= 2 && ea <= 30) {
          icon = IconGood;
        } else if (ea >= 200) {
          icon = IconEvil;
        } else {
          icon = IconNeutral;
        }
      } catch (Exception e) {
        e.printStackTrace();
      }

      // Using cached icons
      String keyIcon = String.format("%1$s%2$s", SharedResourceCache.createKey(icon[0]),
                                                 SharedResourceCache.createKey(icon[1]));
      if (SharedResourceCache.contains(SharedResourceCache.Type.Icon, keyIcon)) {
        icon = ((ResourceIcon)SharedResourceCache.get(SharedResourceCache.Type.Icon, keyIcon)).getData();
        SharedResourceCache.add(SharedResourceCache.Type.Icon, keyIcon);
      } else {
        SharedResourceCache.add(SharedResourceCache.Type.Icon, keyIcon, new ResourceIcon(keyIcon, icon));
      }

      item = new IconLayerItem(location, actor, msg, icon[0], Center);
      item.setName(getCategory());
      item.setToolTipText(msg);
      item.setImage(AbstractLayerItem.ItemState.HIGHLIGHTED, icon[1]);
      item.setVisible(isVisible());
    }
  }
}
