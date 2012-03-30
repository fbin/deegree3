//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2012 by:
 - Department of Geography, University of Bonn -
 and
 - lat/lon GmbH -

 This library is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the Free
 Software Foundation; either version 2.1 of the License, or (at your option)
 any later version.
 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 details.
 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

 Contact information:

 lat/lon GmbH
 Aennchenstr. 19, 53177 Bonn
 Germany
 http://lat-lon.de/

 Department of Geography, University of Bonn
 Prof. Dr. Klaus Greve
 Postfach 1147, 53001 Bonn
 Germany
 http://www.geographie.uni-bonn.de/deegree/

 Occam Labs UG (haftungsbeschränkt)
 Godesberger Allee 139, 53175 Bonn
 Germany
 http://www.occamlabs.de/

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/
package org.deegree.tile.persistence.remotewms;

import static junit.framework.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.deegree.commons.config.DeegreeWorkspace;
import org.deegree.commons.config.ResourceInitException;
import org.deegree.commons.utils.MapUtils;
import org.deegree.cs.exceptions.UnknownCRSException;
import org.deegree.geometry.metadata.SpatialMetadata;
import org.deegree.tile.TileMatrix;
import org.deegree.tile.TileMatrixMetadata;
import org.deegree.tile.TileMatrixSet;
import org.deegree.tile.persistence.TileStoreManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Basic test cases for the {@link RemoteWMSTileStore}.
 * <p>
 * These tests only check the correct extraction of metadata and the generation of the {@link TileMatrixSet}. Actual
 * fetching of tile data is realized as integration tests (module deegree-wmts-tests).
 * </p>
 * 
 * @author <a href="mailto:schneider@occamlabs.de">Markus Schneider</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$
 */
public class RemoteWMSTileStoreTest {

    private DeegreeWorkspace ws;

    @Before
    public void setup()
                            throws UnknownCRSException, IOException, URISyntaxException, ResourceInitException {
        URL wsUrl = RemoteWMSTileStoreTest.class.getResource( "workspace" );
        ws = DeegreeWorkspace.getInstance( "remotewmstilestoretest", new File( wsUrl.toURI() ) );
        ws.initAll();
    }

    @After
    public void tearDown() {
        ws.destroyAll();
    }

    @Test
    public void testGetMetdataEPSG26912() {
        RemoteWMSTileStore store = (RemoteWMSTileStore) ws.getSubsystemManager( TileStoreManager.class ).get( "tiles26912" );
        SpatialMetadata metadata = store.getMetadata();
        assertEquals( 1, metadata.getCoordinateSystems().size() );
        assertEquals( "urn:opengis:def:crs:epsg::26912", metadata.getCoordinateSystems().get( 0 ).getId() );
        assertEquals( 228563.303, metadata.getEnvelope().getMin().get0(), 0.001 );
        assertEquals( 4094785.05, metadata.getEnvelope().getMin().get1(), 0.001 );
        assertEquals( 673991.803, metadata.getEnvelope().getMax().get0(), 0.001 );
        assertEquals( 4653591.55, metadata.getEnvelope().getMax().get1(), 0.001 );
    }

    @Test
    public void testGetTileMatrixSetEPSG26912() {
        RemoteWMSTileStore store = (RemoteWMSTileStore) ws.getSubsystemManager( TileStoreManager.class ).get( "tiles26912" );
        TileMatrixSet matrixSet = store.getTileMatrixSet();
        assertEquals( "image/png", matrixSet.getMetadata().getMimeType() );

        assertEquals( 10, matrixSet.getTileMatrices().size() );
        double scale = 1000.0;
        double resolution = MapUtils.DEFAULT_PIXEL_SIZE * scale;
        for ( TileMatrix matrix : matrixSet.getTileMatrices() ) {
            TileMatrixMetadata md = matrix.getMetadata();
            assertEquals( Double.toString( scale ), md.getIdentifier() );
            assertEquals( resolution, md.getResolution(), 0.001 );
            assertEquals( resolution * md.getTilePixelsX(), md.getTileWidth(), 0.001 );
            assertEquals( resolution * md.getTilePixelsY(), md.getTileHeight(), 0.001 );
            scale *= 2.0;
            resolution *= 2.0;
        }
    }
}
