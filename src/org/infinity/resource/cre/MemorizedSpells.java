// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.cre;

import org.infinity.datatype.Bitmap;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.Unknown;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;

public final class MemorizedSpells extends AbstractStruct implements AddRemovable
{
  // CRE/MemorizedSpells-specific field labels
  public static final String CRE_MEMORIZED              = "Memorized spell";
  public static final String CRE_MEMORIZED_RESREF       = "Spell";
  public static final String CRE_MEMORIZED_MEMORIZATION = "Memorization";

  private static final String[] s_mem = {"Spell already cast", "Spell memorized", "Spell disabled"};

  MemorizedSpells() throws Exception
  {
    super(null, CRE_MEMORIZED, new byte[12], 0);
  }

  MemorizedSpells(AbstractStruct superStruct, byte buffer[], int offset) throws Exception
  {
    super(superStruct, CRE_MEMORIZED, buffer, offset);
  }

//--------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove()
  {
    return true;
  }

//--------------------- End Interface AddRemovable ---------------------

  @Override
  public int read(byte buffer[], int offset) throws Exception
  {
    addField(new ResourceRef(buffer, offset, CRE_MEMORIZED_RESREF, "SPL"));
    addField(new Bitmap(buffer, offset + 8, 2, CRE_MEMORIZED_MEMORIZATION, s_mem));
    addField(new Unknown(buffer, offset + 10, 2));
    return offset + 12;
  }
}
