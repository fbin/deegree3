//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
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

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/
package org.deegree.services.wfs.format.gml;

import static java.math.BigInteger.ZERO;
import static org.deegree.commons.xml.CommonNamespaces.GML3_2_NS;
import static org.deegree.commons.xml.CommonNamespaces.GMLNS;
import static org.deegree.commons.xml.CommonNamespaces.XLNNS;
import static org.deegree.gml.GMLVersion.GML_2;
import static org.deegree.gml.GMLVersion.GML_31;
import static org.deegree.gml.GMLVersion.GML_32;
import static org.deegree.protocol.ows.exception.OWSException.NO_APPLICABLE_CODE;
import static org.deegree.protocol.ows.exception.OWSException.OPERATION_NOT_SUPPORTED;
import static org.deegree.protocol.wfs.WFSConstants.VERSION_100;
import static org.deegree.protocol.wfs.WFSConstants.VERSION_110;
import static org.deegree.protocol.wfs.WFSConstants.VERSION_200;
import static org.deegree.protocol.wfs.WFSConstants.WFS_100_BASIC_SCHEMA_URL;
import static org.deegree.protocol.wfs.WFSConstants.WFS_110_SCHEMA_URL;
import static org.deegree.protocol.wfs.WFSConstants.WFS_200_NS;
import static org.deegree.protocol.wfs.WFSConstants.WFS_200_SCHEMA_URL;
import static org.deegree.protocol.wfs.WFSConstants.WFS_NS;
import static org.deegree.protocol.wfs.WFSConstants.WFS_PREFIX;
import static org.deegree.protocol.wfs.getfeature.ResultType.RESULTS;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.deegree.commons.config.ResourceInitException;
import org.deegree.commons.tom.ResolveParams;
import org.deegree.commons.tom.TypedObjectNode;
import org.deegree.commons.tom.gml.GMLObject;
import org.deegree.commons.tom.gml.GMLReference;
import org.deegree.commons.tom.ows.Version;
import org.deegree.commons.utils.kvp.InvalidParameterValueException;
import org.deegree.commons.utils.time.DateUtils;
import org.deegree.cs.exceptions.TransformationException;
import org.deegree.cs.exceptions.UnknownCRSException;
import org.deegree.feature.Feature;
import org.deegree.feature.FeatureCollection;
import org.deegree.feature.GenericFeatureCollection;
import org.deegree.feature.persistence.FeatureStore;
import org.deegree.feature.persistence.FeatureStoreException;
import org.deegree.feature.persistence.lock.Lock;
import org.deegree.feature.persistence.lock.LockManager;
import org.deegree.feature.persistence.query.Query;
import org.deegree.feature.stream.FeatureInputStream;
import org.deegree.feature.types.FeatureType;
import org.deegree.feature.types.property.FeaturePropertyType;
import org.deegree.feature.types.property.PropertyType;
import org.deegree.feature.xpath.FeatureXPathEvaluator;
import org.deegree.filter.FilterEvaluationException;
import org.deegree.geometry.Envelope;
import org.deegree.geometry.Geometry;
import org.deegree.geometry.io.CoordinateFormatter;
import org.deegree.geometry.io.DecimalCoordinateFormatter;
import org.deegree.gml.GMLOutputFactory;
import org.deegree.gml.GMLStreamWriter;
import org.deegree.gml.GMLVersion;
import org.deegree.gml.feature.GMLFeatureWriter;
import org.deegree.protocol.ows.exception.OWSException;
import org.deegree.protocol.wfs.describefeaturetype.DescribeFeatureType;
import org.deegree.protocol.wfs.getfeature.GetFeature;
import org.deegree.protocol.wfs.getfeature.ResultType;
import org.deegree.protocol.wfs.getfeature.TypeName;
import org.deegree.protocol.wfs.getfeaturewithlock.GetFeatureWithLock;
import org.deegree.protocol.wfs.getgmlobject.GetGmlObject;
import org.deegree.protocol.wfs.getpropertyvalue.GetPropertyValue;
import org.deegree.protocol.wfs.lockfeature.BBoxLock;
import org.deegree.protocol.wfs.lockfeature.FeatureIdLock;
import org.deegree.protocol.wfs.lockfeature.FilterLock;
import org.deegree.protocol.wfs.lockfeature.LockOperation;
import org.deegree.protocol.wfs.query.BBoxQuery;
import org.deegree.protocol.wfs.query.FeatureIdQuery;
import org.deegree.protocol.wfs.query.FilterQuery;
import org.deegree.protocol.wfs.query.ProjectionClause;
import org.deegree.services.controller.OGCFrontController;
import org.deegree.services.controller.utils.HttpResponseBuffer;
import org.deegree.services.i18n.Messages;
import org.deegree.services.jaxb.wfs.GMLFormat.GetFeatureResponse;
import org.deegree.services.wfs.QueryAnalyzer;
import org.deegree.services.wfs.WFSFeatureStoreManager;
import org.deegree.services.wfs.WebFeatureService;
import org.deegree.services.wfs.format.Format;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link Format} implementation that can handle GML 2/3.0/3.1/3.2 and the specific requirements for WFS 2.0
 * response <code>FeatureCollection</code>s (which are not GML feature collections in a strict sense).
 * <p>
 * NOTE: For WFS 1.1.0, some schema communities decided to use a different feature collection element than
 * <code>wfs:FeatureCollection</code>, mostly because <code>wfs:FeatureCollection</code> is bound to GML 3.1. This
 * practice is supported by this {@link Format} implementation for WFS 1.0.0 and WFS 1.1.0 output. However, for WFS 2.0,
 * there's hope that people will refrain from doing so (as WFS 2.0 <code>FeatureCollection</code> allows GML 3.2 output
 * and is not bound to any specific GML version). Therefore, it is currently not supported to use any different output
 * container for WFS 2.0.
 * </p>
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public class GMLFormat implements Format {

    private static final Logger LOG = LoggerFactory.getLogger( GMLFormat.class );

    final GMLVersion gmlVersion;

    private QName responseContainerEl;

    private QName responseFeatureMemberEl;

    private String schemaLocation;

    private boolean disableStreaming;

    private final WebFeatureService master;

    private final WFSFeatureStoreManager service;

    private final int featureLimit;

    private final boolean checkAreaOfUse;

    private CoordinateFormatter formatter;

    private final DescribeFeatureTypeHandler dftHandler;

    private boolean exportOriginalSchema;

    private String appSchemaBaseURL;

    public GMLFormat( WebFeatureService master, GMLVersion gmlVersion ) {
        this.master = master;
        this.service = master.getStoreManager();
        this.dftHandler = new DescribeFeatureTypeHandler( service, exportOriginalSchema, null );
        this.featureLimit = master.getMaxFeatures();
        this.checkAreaOfUse = master.getCheckAreaOfUse();
        this.gmlVersion = gmlVersion;
    }

    public GMLFormat( WebFeatureService master, org.deegree.services.jaxb.wfs.GMLFormat formatDef )
                            throws ResourceInitException {
        this.master = master;
        this.service = master.getStoreManager();

        GetFeatureResponse responseConfig = formatDef.getGetFeatureResponse();
        if ( responseConfig != null ) {
            if ( responseConfig.isDisableStreaming() != null ) {
                disableStreaming = responseConfig.isDisableStreaming();
            }
            if ( responseConfig.getContainerElement() != null ) {
                responseContainerEl = responseConfig.getContainerElement();
            }
            if ( responseConfig.getFeatureMemberElement() != null ) {
                responseFeatureMemberEl = responseConfig.getFeatureMemberElement();
            }
            if ( responseConfig.getAdditionalSchemaLocation() != null ) {
                schemaLocation = responseConfig.getAdditionalSchemaLocation();
            }
            if ( responseConfig.getDisableDynamicSchema() != null ) {
                exportOriginalSchema = responseConfig.getDisableDynamicSchema().isValue();
                appSchemaBaseURL = responseConfig.getDisableDynamicSchema().getBaseURL();
                if ( appSchemaBaseURL != null && appSchemaBaseURL.endsWith( "/" ) ) {
                    appSchemaBaseURL = appSchemaBaseURL.substring( 0, appSchemaBaseURL.length() - 1 );
                }
                if ( appSchemaBaseURL != null && appSchemaBaseURL.isEmpty() ) {
                    appSchemaBaseURL = null;
                }
            }
        }

        this.dftHandler = new DescribeFeatureTypeHandler( service, exportOriginalSchema, appSchemaBaseURL );
        this.featureLimit = master.getMaxFeatures();
        this.checkAreaOfUse = master.getCheckAreaOfUse();

        this.formatter = null;
        try {
            JAXBElement<?> formatterEl = formatDef.getAbstractCoordinateFormatter();
            if ( formatterEl != null ) {
                Object formatterConf = formatterEl.getValue();
                if ( formatterConf instanceof org.deegree.services.jaxb.wfs.DecimalCoordinateFormatter ) {
                    LOG.info( "Setting up configured DecimalCoordinateFormatter." );
                    org.deegree.services.jaxb.wfs.DecimalCoordinateFormatter decimalFormatterConf = (org.deegree.services.jaxb.wfs.DecimalCoordinateFormatter) formatterConf;
                    this.formatter = new DecimalCoordinateFormatter( decimalFormatterConf.getPlaces().intValue() );
                } else if ( formatterConf instanceof org.deegree.services.jaxb.wfs.CustomCoordinateFormatter ) {
                    LOG.info( "Setting up CustomCoordinateFormatter." );
                    org.deegree.services.jaxb.wfs.CustomCoordinateFormatter customFormatterConf = (org.deegree.services.jaxb.wfs.CustomCoordinateFormatter) formatterConf;
                    this.formatter = (CoordinateFormatter) Class.forName( customFormatterConf.getJavaClass() ).newInstance();
                } else {
                    LOG.warn( "Unexpected JAXB type '" + formatterConf.getClass() + "'." );
                }
            }
        } catch ( Exception e ) {
            throw new ResourceInitException( "Error initializing coordinate formatter: " + e.getMessage(), e );
        }

        this.gmlVersion = GMLVersion.valueOf( formatDef.getGmlVersion().value() );
    }

    @Override
    public void destroy() {
        // nothing to do
    }

    @Override
    public void doDescribeFeatureType( DescribeFeatureType request, HttpResponseBuffer response )
                            throws OWSException, XMLStreamException, IOException {
        dftHandler.doDescribeFeatureType( request, response, this );
    }

    @Override
    public void doGetFeature( GetFeature request, HttpResponseBuffer response )
                            throws Exception {
        ResultType type = request.getPresentationParams().getResultType();
        if ( type == RESULTS || type == null ) {
            doResults( request, response );
        } else {
            doHits( request, response );
        }
    }

    @Override
    public void doGetGmlObject( GetGmlObject request, HttpResponseBuffer response )
                            throws Exception {

        LOG.debug( "doGetGmlObject: " + request );
        doSingleObjectResponse( request.getVersion(), request.getOutputFormat(), request.getTraverseXlinkDepth(),
                                request.getRequestedId(), response );
    }

    private void doSingleObjectResponse( Version version, String outputFormat, String traverseXLinkDepthStr, String id,
                                         HttpResponseBuffer response )
                            throws OWSException, XMLStreamException, IOException {

        int resolveDepth = 0;
        if ( traverseXLinkDepthStr != null ) {
            if ( "*".equals( traverseXLinkDepthStr ) ) {
                resolveDepth = -1;
            } else {
                try {
                    resolveDepth = Integer.parseInt( traverseXLinkDepthStr );
                } catch ( NumberFormatException e ) {
                    String msg = Messages.get( "WFS_TRAVERSEXLINKDEPTH_INVALID", traverseXLinkDepthStr );
                    throw new OWSException( new InvalidParameterValueException( msg ) );
                }
            }
        }

        GMLObject o = retrieveObject( id );

        String schemaLocation = null;
        if ( o instanceof Feature ) {
            schemaLocation = WebFeatureService.getSchemaLocation( version, gmlVersion, ( (Feature) o ).getName() );
        } else if ( o instanceof Geometry ) {
            switch ( gmlVersion ) {
            case GML_2:
                schemaLocation = GMLNS + " http://schemas.opengis.net/gml/2.1.2.1/geometry.xsd";
                break;
            case GML_30:
                schemaLocation = GMLNS + " http://schemas.opengis.net/gml/3.0.1/base/geometryComplexes.xsd";
                break;
            case GML_31:
                schemaLocation = GMLNS + " http://schemas.opengis.net/gml/3.1.1/base/geometryComplexes.xsd";
                break;
            case GML_32:
                schemaLocation = GML3_2_NS + " http://schemas.opengis.net/gml/3.2.1/geometryComplexes.xsd";
                break;
            }
        } else {
            String msg = "Error exporting GML object: only exporting of features and geometries is implemented.";
            throw new OWSException( msg, OPERATION_NOT_SUPPORTED );
        }

        String contentType = getContentType( outputFormat, version );
        XMLStreamWriter xmlStream = WebFeatureService.getXMLResponseWriter( response, contentType, schemaLocation );
        GMLStreamWriter gmlStream = GMLOutputFactory.createGMLStreamWriter( gmlVersion, xmlStream );
        gmlStream.setOutputCRS( master.getDefaultQueryCrs() );
        gmlStream.setRemoteXLinkTemplate( getObjectXlinkTemplate( version, gmlVersion ) );
        gmlStream.setXLinkDepth( resolveDepth );
        gmlStream.setCoordinateFormatter( formatter );
        gmlStream.setNamespaceBindings( service.getPrefixToNs() );
        try {
            gmlStream.write( o );
        } catch ( UnknownCRSException e ) {
            String msg = "Error exporting GML object: " + e.getMessage();
            throw new OWSException( msg, NO_APPLICABLE_CODE );
        } catch ( TransformationException e ) {
            String msg = "Error exporting GML object: " + e.getMessage();
            throw new OWSException( msg, NO_APPLICABLE_CODE );
        }
    }

    @Override
    public void doGetPropertyValue( GetPropertyValue request, HttpResponseBuffer response )
                            throws Exception {

        LOG.debug( "doGetPropertyValue: " + request );

        QueryAnalyzer analyzer = new QueryAnalyzer( Collections.singletonList( request.getQuery() ), master, service,
                                                    gmlVersion, checkAreaOfUse );
        String schemaLocation = getSchemaLocation( request.getVersion(), analyzer.getFeatureTypes() );

        int traverseXLinkDepth = 0;
        String xLinkTemplate = getObjectXlinkTemplate( request.getVersion(), gmlVersion );

        if ( request.getResolveParams().getDepth() != null ) {
            if ( "*".equals( request.getResolveParams().getDepth() ) ) {
                traverseXLinkDepth = -1;
            } else {
                try {
                    traverseXLinkDepth = Integer.parseInt( request.getResolveParams().getDepth() );
                } catch ( NumberFormatException e ) {
                    String msg = Messages.get( "WFS_TRAVERSEXLINKDEPTH_INVALID", request.getResolveParams().getDepth() );
                    throw new OWSException( new InvalidParameterValueException( msg ) );
                }
            }
        }
        BigInteger resolveTimeout = request.getResolveParams().getTimeout();

        // quick check if local references in the output can be ruled out
        boolean localReferencesPossible = localReferencesPossible( analyzer, traverseXLinkDepth );

        String contentType = getContentType( request.getPresentationParams().getOutputFormat(), request.getVersion() );
        XMLStreamWriter xmlStream = WebFeatureService.getXMLResponseWriter( response, contentType, schemaLocation );
        xmlStream = new BufferableXMLStreamWriter( xmlStream, xLinkTemplate );

        // open "wfs:ValueCollection" element
        xmlStream.writeStartElement( "wfs", "ValueCollection", WFS_200_NS );
        xmlStream.writeAttribute( "timeStamp", DateUtils.formatISO8601Date( new Date() ) );
        xmlStream.writeAttribute( "numberMatched", "UNKNOWN" );
        xmlStream.writeAttribute( "numberReturned", "UNKNOWN" );

        GMLStreamWriter gmlStream = GMLOutputFactory.createGMLStreamWriter( gmlVersion, xmlStream );
        gmlStream.setRemoteXLinkTemplate( xLinkTemplate );
        gmlStream.setXLinkDepth( traverseXLinkDepth );
        gmlStream.setXLinkExpiry( resolveTimeout == null ? -1 : resolveTimeout.intValue() );
        gmlStream.setProjection( analyzer.getProjection() );
        gmlStream.setOutputCRS( analyzer.getRequestedCRS() );
        gmlStream.setCoordinateFormatter( formatter );
        gmlStream.setNamespaceBindings( service.getPrefixToNs() );
        XlinkedObjectsHandler additionalObjects = new XlinkedObjectsHandler( (BufferableXMLStreamWriter) xmlStream,
                                                                             localReferencesPossible, xLinkTemplate );
        gmlStream.setAdditionalObjectHandler( additionalObjects );
        bindFeatureTypePrefixes( xmlStream, analyzer.getFeatureTypes() );

        // retrieve and write result features
        int numberReturned = 0;
        int maxResults = -1;
        if ( request.getPresentationParams().getCount() != null ) {
            maxResults = request.getPresentationParams().getCount().intValue();
        }

        FeatureXPathEvaluator evaluator = new FeatureXPathEvaluator( gmlVersion );
        GMLFeatureWriter featureWriter = gmlStream.getFeatureWriter();

        for ( Map.Entry<FeatureStore, List<Query>> fsToQueries : analyzer.getQueries().entrySet() ) {
            FeatureStore fs = fsToQueries.getKey();
            Query[] queries = fsToQueries.getValue().toArray( new Query[fsToQueries.getValue().size()] );
            FeatureInputStream rs = fs.query( queries );
            try {
                for ( Feature member : rs ) {
                    TypedObjectNode[] values = evaluator.eval( member, request.getValueReference() );
                    for ( TypedObjectNode value : values ) {
                        xmlStream.writeStartElement( "wfs", "member", WFS_200_NS );
                        featureWriter.export( value, 0, traverseXLinkDepth );
                        xmlStream.writeEndElement();
                        numberReturned++;
                        if ( numberReturned == maxResults ) {
                            break;
                        }
                    }
                    if ( numberReturned == maxResults ) {
                        break;
                    }
                }
            } finally {
                LOG.debug( "Closing FeatureResultSet (stream)" );
                rs.close();
            }
        }

        if ( !additionalObjects.getAdditionalRefs().isEmpty() ) {
            xmlStream.writeStartElement( "wfs", "additionalValues", WFS_200_NS );
            xmlStream.writeStartElement( "wfs", "SimpleFeatureCollection", WFS_200_NS );
            writeAdditionalObjects( request.getVersion(), gmlStream, additionalObjects, traverseXLinkDepth,
                                    xLinkTemplate );
            xmlStream.writeEndElement();
            xmlStream.writeEndElement();
        }

        // close container element
        xmlStream.writeEndElement();
        xmlStream.flush();

        // append buffered parts of the stream
        if ( ( (BufferableXMLStreamWriter) xmlStream ).hasBuffered() ) {
            ( (BufferableXMLStreamWriter) xmlStream ).appendBufferedXML( gmlStream );
        }
    }

    private void doResults( GetFeature request, HttpResponseBuffer response )
                            throws Exception {

        LOG.debug( "Performing GetFeature (results) request." );

        QueryAnalyzer analyzer = new QueryAnalyzer( request.getQueries(), master, service, gmlVersion, checkAreaOfUse );
        String lockId = acquireLock( request, analyzer );

        if ( analyzer.getRequestedFeatureId() != null ) {
            doSingleObjectResponse( request.getVersion(), request.getPresentationParams().getOutputFormat(),
                                    request.getResolveParams().getDepth(), analyzer.getRequestedFeatureId(), response );
            return;
        }

        String schemaLocation = getSchemaLocation( request.getVersion(), analyzer.getFeatureTypes() );

        int traverseXLinkDepth = 0;
        BigInteger resolveTimeout = null;
        String xLinkTemplate = getObjectXlinkTemplate( request.getVersion(), gmlVersion );

        if ( VERSION_110.equals( request.getVersion() ) || VERSION_200.equals( request.getVersion() ) ) {
            if ( request.getResolveParams().getDepth() != null ) {
                if ( "*".equals( request.getResolveParams().getDepth() ) ) {
                    traverseXLinkDepth = -1;
                } else {
                    try {
                        traverseXLinkDepth = Integer.parseInt( request.getResolveParams().getDepth() );
                    } catch ( NumberFormatException e ) {
                        String msg = Messages.get( "WFS_TRAVERSEXLINKDEPTH_INVALID",
                                                   request.getResolveParams().getDepth() );
                        throw new OWSException( new InvalidParameterValueException( msg ) );
                    }
                }
            }
            if ( request.getResolveParams().getTimeout() != null ) {
                resolveTimeout = request.getResolveParams().getTimeout();
                // needed for CITE 1.1.0 compliance (wfs:GetFeature-traverseXlinkExpiry)
                if ( resolveTimeout == null || resolveTimeout.equals( ZERO ) ) {
                    String msg = Messages.get( "WFS_TRAVERSEXLINKEXPIRY_ZERO", resolveTimeout );
                    throw new OWSException( new InvalidParameterValueException( msg ) );
                }
            }
        }

        // quick check if local references in the output can be ruled out
        boolean localReferencesPossible = localReferencesPossible( analyzer, traverseXLinkDepth );

        String contentType = getContentType( request.getPresentationParams().getOutputFormat(), request.getVersion() );
        XMLStreamWriter xmlStream = WebFeatureService.getXMLResponseWriter( response, contentType, schemaLocation );
        xmlStream = new BufferableXMLStreamWriter( xmlStream, xLinkTemplate );

        // open "wfs:FeatureCollection" element
        if ( request.getVersion().equals( VERSION_100 ) ) {
            if ( responseContainerEl != null ) {
                xmlStream.writeStartElement( responseContainerEl.getPrefix(), responseContainerEl.getLocalPart(),
                                             responseContainerEl.getNamespaceURI() );
            } else {
                xmlStream.writeStartElement( "wfs", "FeatureCollection", WFS_NS );
                if ( lockId != null ) {
                    xmlStream.writeAttribute( "lockId", lockId );
                }
            }
        } else if ( request.getVersion().equals( VERSION_110 ) ) {
            if ( responseContainerEl != null ) {
                xmlStream.writeStartElement( responseContainerEl.getPrefix(), responseContainerEl.getLocalPart(),
                                             responseContainerEl.getNamespaceURI() );
            } else {
                xmlStream.writeStartElement( "wfs", "FeatureCollection", WFS_NS );
                if ( lockId != null ) {
                    xmlStream.writeAttribute( "lockId", lockId );
                }
                xmlStream.writeAttribute( "timeStamp", DateUtils.formatISO8601Date( new Date() ) );
            }
        } else if ( request.getVersion().equals( VERSION_200 ) ) {
            xmlStream.writeStartElement( "wfs", "FeatureCollection", WFS_200_NS );
            xmlStream.writeAttribute( "timeStamp", DateUtils.formatISO8601Date( new Date() ) );
        }

        if ( GML_32 == gmlVersion && !request.getVersion().equals( VERSION_200 ) ) {
            xmlStream.writeAttribute( "gml", GML3_2_NS, "id", "WFS_RESPONSE" );
        }

        int maxFeatures = featureLimit;
        if ( request.getPresentationParams().getCount() != null
             && ( maxFeatures == -1 || request.getPresentationParams().getCount().intValue() < maxFeatures ) ) {
            maxFeatures = request.getPresentationParams().getCount().intValue();
        }

        GMLStreamWriter gmlStream = GMLOutputFactory.createGMLStreamWriter( gmlVersion, xmlStream );
        gmlStream.setRemoteXLinkTemplate( xLinkTemplate );
        gmlStream.setXLinkDepth( traverseXLinkDepth );
        gmlStream.setXLinkExpiry( resolveTimeout == null ? -1 : resolveTimeout.intValue() );
        gmlStream.setProjection( analyzer.getProjection() );
        gmlStream.setOutputCRS( analyzer.getRequestedCRS() );
        gmlStream.setCoordinateFormatter( formatter );
        gmlStream.setNamespaceBindings( service.getPrefixToNs() );
        XlinkedObjectsHandler additionalObjects = new XlinkedObjectsHandler( (BufferableXMLStreamWriter) xmlStream,
                                                                             localReferencesPossible, xLinkTemplate );
        gmlStream.setAdditionalObjectHandler( additionalObjects );
        bindFeatureTypePrefixes( xmlStream, analyzer.getFeatureTypes() );

        if ( disableStreaming ) {
            writeFeatureMembersCached( request.getVersion(), gmlStream, analyzer, gmlVersion, xLinkTemplate,
                                       traverseXLinkDepth, maxFeatures );
        } else {
            writeFeatureMembersStream( request.getVersion(), gmlStream, analyzer, gmlVersion, xLinkTemplate,
                                       traverseXLinkDepth, maxFeatures );
        }

        if ( !additionalObjects.getAdditionalRefs().isEmpty() ) {
            if ( request.getVersion().equals( VERSION_200 ) ) {
                xmlStream.writeStartElement( "wfs", "additionalObjects", WFS_200_NS );
                xmlStream.writeStartElement( "wfs", "SimpleFeatureCollection", WFS_200_NS );
            } else {
                xmlStream.writeComment( "Additional features (subfeatures of requested features)" );
            }
            writeAdditionalObjects( request.getVersion(), gmlStream, additionalObjects, traverseXLinkDepth,
                                    xLinkTemplate );
            if ( request.getVersion().equals( VERSION_200 ) ) {
                xmlStream.writeEndElement();
                xmlStream.writeEndElement();
            }
        }

        // close container element
        xmlStream.writeEndElement();
        xmlStream.flush();

        // append buffered parts of the stream
        if ( ( (BufferableXMLStreamWriter) xmlStream ).hasBuffered() ) {
            ( (BufferableXMLStreamWriter) xmlStream ).appendBufferedXML( gmlStream );
        }
    }

    private boolean localReferencesPossible( QueryAnalyzer analyzer, int traverseXLinkDepth ) {
        if ( traverseXLinkDepth == 0 && analyzer.getQueries().size() == 1 ) {
            List<Query> queries = analyzer.getQueries().values().iterator().next();
            if ( queries.size() == 1 ) {
                Query query = queries.get( 0 );
                if ( query.getTypeNames().length == 1 ) {
                    TypeName typeName = query.getTypeNames()[0];
                    FeatureStore fs = analyzer.getQueries().keySet().iterator().next();
                    FeatureType ft = fs.getSchema().getFeatureType( typeName.getFeatureTypeName() );
                    for ( PropertyType pt : ft.getPropertyDeclarations() ) {
                        if ( pt instanceof FeaturePropertyType ) {
                            FeaturePropertyType fpt = (FeaturePropertyType) pt;
                            FeatureType targetFt = fpt.getValueFt();
                            if ( targetFt == null || fs.getSchema().isSubType( targetFt, ft ) ) {
                                return true;
                            }
                        }
                    }
                    LOG.debug( "Forward references can be ruled out." );
                    return false;
                }
            }
        }
        return true;
    }

    private void writeFeatureMembersStream( Version wfsVersion, GMLStreamWriter gmlStream, QueryAnalyzer analyzer,
                                            GMLVersion outputFormat, String xLinkTemplate, int traverseXLinkDepth,
                                            int maxFeatures )
                            throws XMLStreamException, UnknownCRSException, TransformationException,
                            FeatureStoreException, FilterEvaluationException, FactoryConfigurationError, IOException {

        XMLStreamWriter xmlStream = gmlStream.getXMLStream();

        if ( wfsVersion.equals( VERSION_200 ) ) {
            xmlStream.writeAttribute( "numberMatched", "unknown" );
            xmlStream.writeAttribute( "numberReturned", "unknown" );
            xmlStream.writeComment( "NOTE: numberReturned=\"unknown\" does not validate according to the current version of the WFS 2.0 schema. However, there's a change request (CR 144) being worked on that should allow it soon (https://portal.opengeospatial.org/files?artifact_id=43925)." );
        }

        if ( outputFormat == GML_2 ) {
            // "gml:boundedBy" is necessary for GML 2 schema compliance
            xmlStream.writeStartElement( "gml", "boundedBy", GMLNS );
            xmlStream.writeStartElement( GMLNS, "null" );
            xmlStream.writeCharacters( "unknown" );
            xmlStream.writeEndElement();
            xmlStream.writeEndElement();
        }

        // retrieve and write result features
        int featuresAdded = 0;
        for ( Map.Entry<FeatureStore, List<Query>> fsToQueries : analyzer.getQueries().entrySet() ) {
            FeatureStore fs = fsToQueries.getKey();
            Query[] queries = fsToQueries.getValue().toArray( new Query[fsToQueries.getValue().size()] );
            FeatureInputStream rs = fs.query( queries );
            try {
                for ( Feature member : rs ) {
                    writeMemberFeature( member, gmlStream, xmlStream, wfsVersion, xLinkTemplate, 0 );
                    featuresAdded++;
                    if ( featuresAdded == maxFeatures ) {
                        // limit the number of features written to maxfeatures
                        break;
                    }
                }
            } finally {
                LOG.debug( "Closing FeatureResultSet (stream)" );
                rs.close();
            }
        }
    }

    private void writeFeatureMembersCached( Version wfsVersion, GMLStreamWriter gmlStream, QueryAnalyzer analyzer,
                                            GMLVersion outputFormat, String xLinkTemplate, int traverseXLinkDepth,
                                            int maxFeatures )
                            throws XMLStreamException, UnknownCRSException, TransformationException,
                            FeatureStoreException, FilterEvaluationException, FactoryConfigurationError, IOException {

        FeatureCollection allFeatures = new GenericFeatureCollection();
        Set<String> fids = new HashSet<String>();

        // retrieve maxfeatures features
        int featuresAdded = 0;
        for ( Map.Entry<FeatureStore, List<Query>> fsToQueries : analyzer.getQueries().entrySet() ) {
            FeatureStore fs = fsToQueries.getKey();
            Query[] queries = fsToQueries.getValue().toArray( new Query[fsToQueries.getValue().size()] );
            FeatureInputStream rs = fs.query( queries );
            try {
                for ( Feature feature : rs ) {
                    if ( !fids.contains( feature.getId() ) ) {
                        allFeatures.add( feature );
                        fids.add( feature.getId() );
                        featuresAdded++;
                        if ( featuresAdded == maxFeatures ) {
                            break;
                        }
                    }
                }
            } finally {
                LOG.debug( "Closing FeatureResultSet (cached)" );
                rs.close();
            }
        }

        XMLStreamWriter xmlStream = gmlStream.getXMLStream();
        if ( wfsVersion.equals( VERSION_200 ) ) {
            xmlStream.writeAttribute( "numberMatched", "" + allFeatures.size() );
            xmlStream.writeAttribute( "numberReturned", "" + allFeatures.size() );
        } else if ( !wfsVersion.equals( VERSION_100 ) && responseContainerEl == null ) {
            xmlStream.writeAttribute( "numberOfFeatures", "" + allFeatures.size() );
        }

        if ( outputFormat == GML_2 || allFeatures.getEnvelope() != null ) {
            writeBoundedBy( gmlStream, outputFormat, allFeatures.getEnvelope() );
        }

        // retrieve and write result features
        for ( Feature member : allFeatures ) {
            writeMemberFeature( member, gmlStream, xmlStream, wfsVersion, xLinkTemplate, 0 );
        }
    }

    private void writeAdditionalObjects( Version wfsVersion, GMLStreamWriter gmlStream,
                                         XlinkedObjectsHandler additionalObjects, int traverseXLinkDepth,
                                         String xLinkTemplate )
                            throws XMLStreamException, UnknownCRSException, TransformationException {

        int currentLevel = 1;
        Collection<GMLReference<?>> includeObjects = additionalObjects.getAdditionalRefs();

        while ( ( traverseXLinkDepth == -1 || currentLevel <= traverseXLinkDepth ) && !includeObjects.isEmpty() ) {
            additionalObjects.clear();
            for ( GMLReference<?> gmlReference : includeObjects ) {
                Feature feature = (Feature) gmlReference;
                writeMemberFeature( feature, gmlStream, gmlStream.getXMLStream(), wfsVersion, xLinkTemplate,
                                    currentLevel );
            }
            includeObjects = additionalObjects.getAdditionalRefs();
            currentLevel++;
        }
    }

    private void writeMemberFeature( Feature member, GMLStreamWriter gmlStream, XMLStreamWriter xmlStream,
                                     Version wfsVersion, String xLinkTemplate, int level )
                            throws XMLStreamException, UnknownCRSException, TransformationException {

        if ( gmlStream.isObjectExported( member.getId() ) ) {
            if ( VERSION_200.equals( wfsVersion ) ) {
                xmlStream.writeEmptyElement( "wfs", "member", WFS_200_NS );
            } else if ( responseFeatureMemberEl != null ) {
                xmlStream.writeEmptyElement( responseFeatureMemberEl.getPrefix(),
                                             responseFeatureMemberEl.getLocalPart(),
                                             responseFeatureMemberEl.getNamespaceURI() );
            } else if ( GML_32 == gmlVersion ) {
                xmlStream.writeEmptyElement( "gml", "featureMember", GML3_2_NS );
            } else {
                xmlStream.writeEmptyElement( "gml", "featureMember", GMLNS );
            }
            xmlStream.writeAttribute( "xlink", XLNNS, "href", "#" + member.getId() );
        } else {
            if ( VERSION_200.equals( wfsVersion ) ) {
                xmlStream.writeStartElement( "wfs", "member", WFS_200_NS );
            } else if ( responseFeatureMemberEl != null ) {
                xmlStream.writeStartElement( responseFeatureMemberEl.getPrefix(),
                                             responseFeatureMemberEl.getLocalPart(),
                                             responseFeatureMemberEl.getNamespaceURI() );
            } else if ( GML_32 == gmlVersion ) {
                xmlStream.writeStartElement( "gml", "featureMember", GML3_2_NS );
            } else {
                xmlStream.writeStartElement( "gml", "featureMember", GMLNS );
            }
            gmlStream.getFeatureWriter().export( member, level );
            xmlStream.writeEndElement();
        }
    }

    private void writeBoundedBy( GMLStreamWriter gmlStream, GMLVersion outputFormat, Envelope env )
                            throws XMLStreamException, UnknownCRSException, TransformationException {

        XMLStreamWriter xmlStream = gmlStream.getXMLStream();
        switch ( outputFormat ) {
        case GML_2: {
            xmlStream.writeStartElement( "gml", "boundedBy", GMLNS );
            if ( env == null ) {
                xmlStream.writeStartElement( "gml", "null", GMLNS );
                xmlStream.writeCharacters( "inapplicable" );
                xmlStream.writeEndElement();
            } else {
                gmlStream.write( env );
            }
            xmlStream.writeEndElement();
            break;
        }
        case GML_30:
        case GML_31: {
            xmlStream.writeStartElement( "gml", "boundedBy", GMLNS );
            if ( env == null ) {
                xmlStream.writeStartElement( "gml", "Null", GMLNS );
                xmlStream.writeCharacters( "inapplicable" );
                xmlStream.writeEndElement();
            } else {
                gmlStream.write( env );
            }
            xmlStream.writeEndElement();
            break;
        }
        case GML_32: {
            xmlStream.writeStartElement( "gml", "boundedBy", GML3_2_NS );
            if ( env == null ) {
                xmlStream.writeStartElement( "gml", "Null", GML3_2_NS );
                xmlStream.writeCharacters( "inapplicable" );
                xmlStream.writeEndElement();
            } else {
                gmlStream.write( env );
            }
            xmlStream.writeEndElement();
            break;
        }
        }
    }

    private void bindFeatureTypePrefixes( XMLStreamWriter xmlStream, Collection<FeatureType> fts )
                            throws XMLStreamException {

        if ( fts == null ) {
            fts = service.getFeatureTypes();
        }

        Map<String, String> nsToPrefix = new HashMap<String, String>();
        for ( FeatureType ft : fts ) {
            QName ftName = ft.getName();
            if ( ftName.getPrefix() != null ) {
                nsToPrefix.put( ftName.getNamespaceURI(), ftName.getPrefix() );
            }
        }

        for ( Map.Entry<String, String> nsBinding : nsToPrefix.entrySet() ) {
            xmlStream.setPrefix( nsBinding.getValue(), nsBinding.getKey() );
        }
    }

    private void doHits( GetFeature request, HttpResponseBuffer response )
                            throws OWSException, XMLStreamException, IOException, FeatureStoreException,
                            FilterEvaluationException {

        LOG.debug( "Performing GetFeature (hits) request." );

        QueryAnalyzer analyzer = new QueryAnalyzer( request.getQueries(), master, service, gmlVersion, checkAreaOfUse );
        String lockId = acquireLock( request, analyzer );
        String schemaLocation = getSchemaLocation( request.getVersion(), analyzer.getFeatureTypes() );

        String contentType = getContentType( request.getPresentationParams().getOutputFormat(), request.getVersion() );
        XMLStreamWriter xmlStream = WebFeatureService.getXMLResponseWriter( response, contentType, schemaLocation );

        // open "wfs:FeatureCollection" element
        if ( request.getVersion().equals( VERSION_100 ) ) {
            xmlStream.writeStartElement( "wfs", "FeatureCollection", WFS_NS );
            if ( lockId != null ) {
                xmlStream.writeAttribute( "lockId", lockId );
            }
        } else if ( request.getVersion().equals( VERSION_110 ) ) {
            xmlStream.writeStartElement( "wfs", "FeatureCollection", WFS_NS );
            if ( lockId != null ) {
                xmlStream.writeAttribute( "lockId", lockId );
            }
            xmlStream.writeAttribute( "timeStamp", DateUtils.formatISO8601Date( new Date() ) );
        } else if ( request.getVersion().equals( VERSION_200 ) ) {
            xmlStream.writeStartElement( "wfs", "FeatureCollection", WFS_200_NS );
            xmlStream.writeAttribute( "timeStamp", DateUtils.formatISO8601Date( new Date() ) );
        }

        int numHits = 0;

        for ( Map.Entry<FeatureStore, List<Query>> fsToQueries : analyzer.getQueries().entrySet() ) {
            FeatureStore fs = fsToQueries.getKey();
            Query[] queries = fsToQueries.getValue().toArray( new Query[fsToQueries.getValue().size()] );
            // TODO what about features that occur multiple times as result of different queries?
            numHits += fs.queryHits( queries );
        }

        xmlStream.writeAttribute( "numberOfFeatures", "" + numHits );

        // "gml:boundedBy" is necessary for GML 2 schema compliance
        if ( gmlVersion.equals( GMLVersion.GML_2 ) ) {
            xmlStream.writeStartElement( "gml", "boundedBy", GMLNS );
            xmlStream.writeStartElement( GMLNS, "null" );
            xmlStream.writeCharacters( "unknown" );
            xmlStream.writeEndElement();
            xmlStream.writeEndElement();
        }

        // close "wfs:FeatureCollection"
        xmlStream.writeEndElement();
        xmlStream.flush();

    }

    /**
     * Returns the value for the <code>xsi:schemaLocation</code> attribute in the response document.
     * 
     * @param requestVersion
     *            requested WFS version, must not be <code>null</code>
     * @param requestedFts
     *            requested feature types, can be <code>null</code> (any feature type may occur in the output)
     * @return value for the <code>xsi:schemaLocation</code> attribute, never <code>null</code>
     */
    private String getSchemaLocation( Version requestVersion, Collection<FeatureType> requestedFts ) {

        String schemaLocation = null;
        if ( !VERSION_200.equals( requestVersion ) ) {
            schemaLocation = this.schemaLocation;
        } else {
            schemaLocation = WFS_200_NS + " " + WFS_200_SCHEMA_URL;
        }
        if ( responseContainerEl == null ) {
            // use "wfs:FeatureCollection" then
            QName wfsFeatureCollection = new QName( WFS_NS, "FeatureCollection", WFS_PREFIX );
            if ( responseContainerEl == null || wfsFeatureCollection.equals( responseContainerEl ) ) {
                if ( VERSION_100.equals( requestVersion ) ) {
                    if ( GML_2 == gmlVersion ) {
                        schemaLocation = WFS_NS + " " + WFS_100_BASIC_SCHEMA_URL;
                    } else {
                        schemaLocation = WebFeatureService.getSchemaLocation( requestVersion, gmlVersion,
                                                                              wfsFeatureCollection );
                    }
                } else if ( VERSION_110.equals( requestVersion ) ) {
                    if ( GML_31 == gmlVersion ) {
                        schemaLocation = WFS_NS + " " + WFS_110_SCHEMA_URL;
                    } else {
                        schemaLocation = WebFeatureService.getSchemaLocation( requestVersion, gmlVersion,
                                                                              wfsFeatureCollection );
                    }
                }
            }
        }

        if ( requestedFts == null ) {
            requestedFts = service.getFeatureTypes();
        }

        QName[] requestedFtNames = new QName[requestedFts.size()];
        int i = 0;
        for ( FeatureType requestedFt : requestedFts ) {
            requestedFtNames[i++] = requestedFt.getName();
        }

        if ( schemaLocation == null || schemaLocation.isEmpty() ) {
            schemaLocation = WebFeatureService.getSchemaLocation( requestVersion, gmlVersion, requestedFtNames );
        } else {
            schemaLocation += " " + WebFeatureService.getSchemaLocation( requestVersion, gmlVersion, requestedFtNames );
        }

        return schemaLocation;
    }

    /**
     * Returns the content type header for the HTTP response.
     * 
     * @param outputFormat
     *            requested output format, may be <code>null</code>
     * @param version
     *            request version, must not be <code>null</code>
     * @return content type for the http header, never <code>null</code>
     */
    static String getContentType( String outputFormat, Version version ) {

        String contentType = outputFormat;
        if ( outputFormat == null ) {
            if ( VERSION_100.equals( version ) ) {
                contentType = "text/xml; subtype=gml/2.1.2";
            } else if ( VERSION_110.equals( version ) ) {
                contentType = "text/xml; subtype=gml/3.1.1";
            } else if ( VERSION_200.equals( version ) ) {
                contentType = "text/xml; subtype=gml/3.2.1";
            }
        }
        return contentType;
    }

    private String acquireLock( GetFeature request, QueryAnalyzer analyzer )
                            throws OWSException {

        String lockId = null;

        if ( request instanceof GetFeatureWithLock ) {
            GetFeatureWithLock gfLock = (GetFeatureWithLock) request;

            // CITE 1.1.0 compliance (wfs:GetFeatureWithLock-Xlink)
            if ( analyzer.getProjection() != null ) {
                for ( ProjectionClause clause : analyzer.getProjection() ) {
                    ResolveParams resolveParams = clause.getResolveParams();
                    if ( resolveParams.getDepth() != null || resolveParams.getMode() != null
                         || resolveParams.getTimeout() != null ) {
                        throw new OWSException( "GetFeatureWithLock does not support XlinkPropertyName",
                                                OWSException.OPTION_NOT_SUPPORTED );
                    }
                }
            }

            boolean mustLockAll = true;

            // default: 5 minutes
            int expiry = 5 * 60 * 1000;
            if ( gfLock.getExpiry() != null ) {
                expiry = gfLock.getExpiry() * 60 * 1000;
            }

            LockManager manager = null;
            try {
                // TODO strategy for multiple LockManagers / feature stores
                manager = service.getStores()[0].getLockManager();

                LockOperation[] lockOperations = new LockOperation[request.getQueries().size()];
                int i = 0;
                for ( org.deegree.protocol.wfs.query.Query wfsQuery : request.getQueries() ) {
                    lockOperations[i++] = buildLockOperation( wfsQuery );
                }
                Lock lock = manager.acquireLock( lockOperations, mustLockAll, expiry );
                lockId = lock.getId();
            } catch ( FeatureStoreException e ) {
                throw new OWSException( "Cannot acquire lock: " + e.getMessage(), NO_APPLICABLE_CODE );
            }
        }
        return lockId;
    }

    private LockOperation buildLockOperation( org.deegree.protocol.wfs.query.Query wfsQuery ) {
        LockOperation lockOperation = null;
        if ( wfsQuery instanceof BBoxQuery ) {
            BBoxQuery bboxQuery = (BBoxQuery) wfsQuery;
            lockOperation = new BBoxLock( bboxQuery.getBBox(), bboxQuery.getTypeNames() );
        } else if ( wfsQuery instanceof FeatureIdQuery ) {
            FeatureIdQuery fidQuery = (FeatureIdQuery) wfsQuery;
            String[] fids = fidQuery.getFeatureIds();
            lockOperation = new FeatureIdLock( fids, fidQuery.getTypeNames() );
        } else if ( wfsQuery instanceof FilterQuery ) {
            FilterQuery filterQuery = (FilterQuery) wfsQuery;
            // TODO multiple type names
            lockOperation = new FilterLock( null, filterQuery.getTypeNames()[0], filterQuery.getFilter() );
        } else {
            throw new RuntimeException();
        }
        return lockOperation;
    }

    private GMLObject retrieveObject( String id )
                            throws OWSException {
        GMLObject o = null;
        for ( FeatureStore fs : service.getStores() ) {
            try {
                o = fs.getObjectById( id );
            } catch ( FeatureStoreException e ) {
                throw new OWSException( e.getMessage(), NO_APPLICABLE_CODE );
            }
            if ( o != null ) {
                break;
            }
        }
        if ( o == null ) {
            String msg = Messages.getMessage( "WFS_NO_SUCH_OBJECT", id );
            throw new OWSException( new InvalidParameterValueException( msg ) );
        }
        return o;
    }

    /**
     * Returns an URL template for requesting individual objects (feature or geometries) from the server by the object's
     * id.
     * <p>
     * The form of the URL depends on the protocol version:
     * <ul>
     * <li>WFS 1.0.0: GetGmlObject request (actually a 1.1.0 request, as 1.0.0 doesn't have it)</li>
     * <li>WFS 1.1.0: GetGmlObject request</li>
     * <li>WFS 2.0.0: GetFeature request using stored query (urn:ogc:def:query:OGC-WFS::GetFeatureById)</li>
     * </ul>
     * </p>
     * 
     * @param version
     *            WFS protocol version, must not be <code>null</code>
     * @param gmlVersion
     *            GML version, must not be <code>null</code>
     * @return URI template that contains <code>{}</code> as the placeholder for the object id
     * @throws UnsupportedOperationException
     *             if the protocol version does not support requesting individual objects by id
     */
    private String getObjectXlinkTemplate( Version version, GMLVersion gmlVersion ) {

        String baseUrl = OGCFrontController.getHttpGetURL() + "SERVICE=WFS&VERSION=" + version + "&";
        String template = null;
        try {
            if ( VERSION_100.equals( version ) ) {
                baseUrl = OGCFrontController.getHttpGetURL() + "SERVICE=WFS&VERSION=1.1.0&";
                template = baseUrl + "REQUEST=GetGmlObject&OUTPUTFORMAT="
                           + URLEncoder.encode( gmlVersion.getMimeTypeOldStyle(), "UTF-8" )
                           + "&TRAVERSEXLINKDEPTH=0&GMLOBJECTID={}#{}";
            } else if ( VERSION_110.equals( version ) ) {
                template = baseUrl + "REQUEST=GetGmlObject&OUTPUTFORMAT="
                           + URLEncoder.encode( gmlVersion.getMimeTypeOldStyle(), "UTF-8" )
                           + "&TRAVERSEXLINKDEPTH=0&GMLOBJECTID={}#{}";
            } else if ( VERSION_200.equals( version ) ) {
                template = baseUrl + "REQUEST=GetFeature&OUTPUTFORMAT="
                           + URLEncoder.encode( gmlVersion.getMimeType(), "UTF-8" )
                           + "&STOREDQUERY_ID=urn:ogc:def:query:OGC-WFS::GetFeatureById&ID={}#{}";
            } else {
                throw new UnsupportedOperationException( Messages.getMessage( "WFS_BACKREFERENCE_UNSUPPORTED", version ) );
            }
        } catch ( UnsupportedEncodingException e ) {
            // should never happen (UTF-8 is known)
        }
        return template;
    }
}