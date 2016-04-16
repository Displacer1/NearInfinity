// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.dlg;

import java.nio.ByteBuffer;

import org.infinity.datatype.DecNumber;
import org.infinity.datatype.StringRef;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.AddRemovable;
import org.infinity.util.io.StreamUtils;

public final class State extends AbstractStruct implements AddRemovable
{
  // DLG/State-specific field labels
  public static final String DLG_STATE                      = "State";
  public static final String DLG_STATE_RESPONSE             = "Response";
  public static final String DLG_STATE_FIRST_RESPONSE_INDEX = "First response index";
  public static final String DLG_STATE_NUM_RESPONSES        = "# responses";
  public static final String DLG_STATE_TRIGGER_INDEX        = "Trigger index";

  private int nr;

  State() throws Exception
  {
    super(null, DLG_STATE, StreamUtils.getByteBuffer(16), 0);
  }

  State(AbstractStruct superStruct, ByteBuffer buffer, int offset, int count) throws Exception
  {
    super(superStruct, DLG_STATE + " " + count, buffer, offset);
    nr = count;
  }

  public int getFirstTrans()
  {
    return ((DecNumber)getAttribute(DLG_STATE_FIRST_RESPONSE_INDEX)).getValue();
  }

  public int getNumber()
  {
    return nr;
  }

  public StringRef getResponse()
  {
    return (StringRef)getAttribute(DLG_STATE_RESPONSE);
  }

  public int getTransCount()
  {
    return ((DecNumber)getAttribute(DLG_STATE_NUM_RESPONSES)).getValue();
  }

  public int getTriggerIndex()
  {
    return ((DecNumber)getAttribute(DLG_STATE_TRIGGER_INDEX)).getValue();
  }

//--------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove()
  {
    return true;
  }

//--------------------- End Interface AddRemovable ---------------------

  @Override
  public int read(ByteBuffer buffer, int offset)
  {
    addField(new StringRef(buffer, offset, DLG_STATE_RESPONSE));
    addField(new DecNumber(buffer, offset + 4, 4, DLG_STATE_FIRST_RESPONSE_INDEX));
    addField(new DecNumber(buffer, offset + 8, 4, DLG_STATE_NUM_RESPONSES));
    addField(new DecNumber(buffer, offset + 12, 4, DLG_STATE_TRIGGER_INDEX));
    return offset + 16;
  }
}

