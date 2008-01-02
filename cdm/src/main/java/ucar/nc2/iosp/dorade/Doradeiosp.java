// $Id: Doradeiosp.java,v 1.12 2006/04/19 20:24:49 yuanho Exp $
/*
 * Copyright 1997-2004 Unidata Program Center/University Corporation for
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
 * along with this library; if not, strlenwrite to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package ucar.nc2.iosp.dorade;

import ucar.ma2.*;
import ucar.nc2.*;
import ucar.nc2.iosp.AbstractIOServiceProvider;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.atd.dorade.*;


import java.io.*;
import java.util.*;
//import java.awt.image.BufferedImage;

/**
 * IOServiceProvider implementation abstract base class to read/write "version 3" netcdf files.
 *  AKA "file format version 1" files.
 *
 *  see   concrete class
 */

public class Doradeiosp extends AbstractIOServiceProvider {

  protected boolean readonly;
  private ucar.nc2.NetcdfFile ncfile;
  private ucar.unidata.io.RandomAccessFile myRaf;
  protected Doradeheader headerParser;

  public DoradeSweep mySweep = null;
  boolean littleEndian;

  public boolean isValidFile(ucar.unidata.io.RandomAccessFile raf) {
    return (Doradeheader.isValidFile(raf));
  }

  /////////////////////////////////////////////////////////////////////////////
  // reading

  public void open(ucar.unidata.io.RandomAccessFile raf, ucar.nc2.NetcdfFile file,
                   ucar.nc2.util.CancelTask cancelTask) throws IOException {

    ncfile = file;
    myRaf = raf;

    try {
        mySweep = new DoradeSweep(raf.getRandomAccessFile());
     } catch (DoradeSweep.DoradeSweepException ex) {
            ex.printStackTrace();

    } catch (java.io.IOException ex) {
            ex.printStackTrace();
    }

    if(mySweep.getScanMode(0)  != ScanMode.MODE_SUR) {
            System.err.println("ScanMode is : " + mySweep.getScanMode(0).getName());
            //System.exit(1);
	}
    try {
       headerParser = new Doradeheader();
       headerParser.read(mySweep, ncfile, null);
    } catch (DoradeSweep.DoradeSweepException e) {
        e.printStackTrace(); 
    }

    ncfile.finish();


  }


  public Array readData(ucar.nc2.Variable v2, Section section) throws IOException, InvalidRangeException  {

    Array outputData = null;
    int nSensor = mySweep.getNSensors();
    int nRays = mySweep.getNRays();
    List<Range> ranges = section.getRanges();

    if ( v2.getName().equals( "elevation"))
    {
        float [] elev = mySweep.getElevations();
        outputData = readData1(v2, ranges, elev);
        //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), elev);
    }
    else if ( v2.getName().equals( "rays_time"))
    {
        Date [] dd = mySweep.getTimes();
        double [] d  = new double [dd.length];
        for(int i = 0; i < dd.length; i++ )
            d[i] = (double) dd[i].getTime();
        outputData = readData2(v2, ranges, d);
        //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), d);
    }
    else if (v2.getName().equals( "azimuth"))
    {
        float [] azim = mySweep.getAzimuths();
        outputData = readData1(v2, ranges, azim);
        //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), azim);
    }
    else if (v2.getName().startsWith( "latitudes_"))
    {
        float [] allLats = new float[nSensor*nRays];
        float [] lats = null;
        for (int i = 0; i < nSensor; i++){
           lats = mySweep.getLatitudes(i);
           System.arraycopy(lats, 0, allLats, i*nRays, nRays);
        }
        outputData = readData1(v2, ranges, allLats);
        //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), allLats);
    }
    else if (v2.getName().startsWith( "longitudes_"))
    {
        float [] allLons = new float[nSensor*nRays];
        float [] lons = null;
        for (int i = 0; i < nSensor; i++){
           lons = mySweep.getLongitudes(i);
           System.arraycopy(lons, 0, allLons, i*nRays, nRays);
        }
        outputData = readData1(v2, ranges, allLons);
        //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), lons);
    }
    else if (v2.getName().startsWith( "altitudes_"))
    {
        float [] allAlts = new float [nSensor*nRays];
        float [] alts = null;
        for (int i = 0; i < nSensor; i++){
           alts = mySweep.getAltitudes(i);
           System.arraycopy(alts, 0, allAlts, i*nRays, nRays);
        }
        outputData = readData1(v2, ranges, allAlts);
        //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), alts);
    }
    else if (v2.getName().startsWith( "distance_"))
    {
        float [] dist = null;
        int j = 0;
        for (int i = 0; i < nSensor; i++)  {
            String t  = "" + i;
            if( v2.getName().endsWith(t) ) {
                j = i ;
                break;
            }
        }
        int nc = mySweep.getNCells(j);
        Array data = NetcdfDataset.makeArray( DataType.FLOAT, nc,
          (double) mySweep.getRangeToFirstCell(j), (double) mySweep.getCellSpacing(j));
        dist = (float [])data.get1DJavaArray(Float.class);
        outputData = readData1(v2, ranges, dist);;

    }
    else if(v2.isScalar())
    {

        float d = 0.0f;

        if( v2.getName().equals("Range_to_First_Cell") )
        {
              d = mySweep.getRangeToFirstCell(0);
        }
        else if (v2.getName().equals("Cell_Spacing"))
        {
              d = mySweep.getCellSpacing(0);
        }
        else if (v2.getName().equals("Fixed_Angle"))
        {
              d = mySweep.getFixedAngle();
        }
        else if (v2.getName().equals("Nyquist_Velocity"))
        {
              d = mySweep.getUnambiguousVelocity(0);
        }
        else if (v2.getName().equals("Unambiguous_Range"))
        {
              d = mySweep.getunambiguousRange(0);
        }
        else if (v2.getName().equals("Radar_Constant"))
        {
              d = mySweep.getradarConstant(0);
        }else if (v2.getName().equals("rcvr_gain"))
        {
              d = mySweep.getrcvrGain(0);
        }
        else if (v2.getName().equals("ant_gain"))
        {
              d = mySweep.getantennaGain(0);
        }
        else if (v2.getName().equals("sys_gain"))
        {
              d = mySweep.getsystemGain(0);
        }
        else if (v2.getName().equals("bm_width"))
        {
              d = mySweep.gethBeamWidth(0);
        }
       float [] dd = new float[1];
       dd[0] = d;
       outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), dd);
    }
    else
    {
        Range radialRange = section.getRange(0);
        Range gateRange = section.getRange(1);

        Array data = Array.factory(v2.getDataType().getPrimitiveClassType(), section.getShape());
        IndexIterator ii = data.getIndexIterator();

        DoradePARM dp = mySweep.lookupParamIgnoreCase(v2.getName());
        int ncells = dp.getNCells();
        float[] rayValues = new float[ncells];
       /*
        float[] allValues = new float[nRays * ncells];
        for (int r = 0; r < nRays; r++) {
            try {
                rayValues = mySweep.getRayData(dp, r, rayValues);
            } catch (DoradeSweep.DoradeSweepException ex) {
                 ex.printStackTrace();
            }
            System.arraycopy(rayValues, 0, allValues, r*ncells, ncells);
        }    */
        for (int r=radialRange.first(); r<=radialRange.last(); r+= radialRange.stride()) {
            try {
                rayValues = mySweep.getRayData(dp, r, rayValues);
            } catch (DoradeSweep.DoradeSweepException ex) {
                 ex.printStackTrace();
            }
            for (int i=gateRange.first(); i<=gateRange.last(); i+= gateRange.stride()){
                  ii.setFloatNext( rayValues[i]);
            }

        }
        return data;
        //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), allValues);
    }

    return outputData;


  }

  public Array readData1(Variable v2, java.util.List section, float[] values)
  {
      Array data = Array.factory(v2.getDataType().getPrimitiveClassType(), Range.getShape( section));
      IndexIterator ii = data.getIndexIterator();
      Range radialRange = (Range) section.get(0);     // radial range can also be gate range

      for (int r=radialRange.first(); r<=radialRange.last(); r+= radialRange.stride()) {
             ii.setFloatNext( values[r]);
      }

      return data;
  }

  public Array readData2(Variable v2, java.util.List section, double[] values)
  {
      Array data = Array.factory(v2.getDataType().getPrimitiveClassType(), Range.getShape( section));
      IndexIterator ii = data.getIndexIterator();
      Range radialRange = (Range) section.get(0);

      for (int r=radialRange.first(); r<=radialRange.last(); r+= radialRange.stride()) {
             ii.setDoubleNext(values[r]);
      }

      return data;
  }


    // for the compressed data read all out into a array and then parse into requested


  protected boolean fill;
  protected HashMap dimHash = new HashMap(50);

  public void flush() throws java.io.IOException {
    myRaf.flush();
  }

  public void close() throws java.io.IOException {
    myRaf.close();
  }


  public static void main(String args[]) throws Exception, IOException, InstantiationException, IllegalAccessException {
    String fileIn = "/home/yuanho/dorade/swp.1020511015815.SP0L.573.1.2_SUR_v1";
    //String fileIn = "c:/data/image/Dorade/n0r_20041013_1852";
    ucar.nc2.NetcdfFile.registerIOProvider( ucar.nc2.iosp.dorade.Doradeiosp.class);
    ucar.nc2.NetcdfFile ncf = ucar.nc2.NetcdfFile.open(fileIn);

    //List alist = ncf.getGlobalAttributes();
    ucar.unidata.io.RandomAccessFile file = new ucar.unidata.io.RandomAccessFile(fileIn, "r");

    //open1(file, null, null);
    //ucar.nc2.Variable v = ncf.findVariable("BaseReflectivity");

    //ncf.close();


  }


}

