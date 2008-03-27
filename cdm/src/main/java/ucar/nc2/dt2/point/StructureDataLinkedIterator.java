/*
 * Copyright 1997-2008 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package ucar.nc2.dt2.point;

import ucar.nc2.dt2.PointFeatureIterator;
import ucar.nc2.dt2.PointFeature;
import ucar.nc2.Structure;
import ucar.ma2.StructureData;

import java.io.IOException;

/**
 * Use linked lists to iterate over members of a Structure, with optional filtering.
 * Subclass must implement makeFeature().
 * @author caron
 * @since Mar 26, 2008
 */
public abstract class StructureDataLinkedIterator implements PointFeatureIterator {
  static private org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StructureDataLinkedIterator.class);

  private Structure s;
  private int nextRecno, lastRecord;
  private String linkVarName;
  private Filter filter;

  private PointFeature feature;

  protected abstract PointFeature makeFeature(int recnum, StructureData sdata) throws IOException;

  public StructureDataLinkedIterator(Structure s, int firstRecord, int lastRecord, String linkVarName, Filter filter) {
    this.s = s;
    this.nextRecno = firstRecord;
    this.lastRecord = lastRecord; // contiguous only
    this.linkVarName = linkVarName;
    this.filter = filter;
  }

  public boolean hasNext() throws IOException {
    while (nextRecno > 0) {
      int recno = nextRecno;
      StructureData sdata = nextStructureData();
      feature = makeFeature(recno, sdata);
      if (filter != null && !filter.filter(feature)) continue;
      return true;
    }
    feature = null;
    return false;
  }

  public PointFeature nextData() throws IOException {
    return feature;
  }

  private StructureData nextStructureData() throws IOException {
    StructureData sdata;
    int recno = nextRecno;
    try {
      sdata = s.readStructure(recno);
    } catch (ucar.ma2.InvalidRangeException e) {
      log.error("StructureDataLinkedIterator.nextStructureData recno=" + recno, e);
      throw new IOException(e.getMessage());
    }

    if (lastRecord > 0) {
      nextRecno++;
      if (nextRecno > lastRecord)
        nextRecno = -1;
    } else {
      nextRecno = sdata.getScalarInt(linkVarName);
    }

    return sdata;
  }

  public void setBufferSize(int bytes) {
    // no op
  }
}
