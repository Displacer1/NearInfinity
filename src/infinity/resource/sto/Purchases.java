// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.resource.sto;

import infinity.datatype.Bitmap;
import infinity.resource.AddRemovable;
import infinity.resource.Profile;
import infinity.resource.itm.ItmResource;

final class Purchases extends Bitmap implements AddRemovable
{
  Purchases()
  {
    super(new byte[4], 0, 4, "Store purchases",
          ((Boolean)Profile.getProperty(Profile.IS_SUPPORTED_STO_V11)) ? ItmResource.s_categories11
                                                                       : ItmResource.s_categories);
  }

  Purchases(byte buffer[], int offset, int number)
  {
    super(buffer, offset, 4, "Store purchases " + number,
          ((Boolean)Profile.getProperty(Profile.IS_SUPPORTED_STO_V11)) ? ItmResource.s_categories11
                                                                       : ItmResource.s_categories);
  }

//--------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove()
  {
    return true;
  }

//--------------------- End Interface AddRemovable ---------------------
}

