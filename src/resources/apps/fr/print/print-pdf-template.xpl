<?xml version="1.0" encoding="utf-8"?>
<!--
  Copyright (C) 2010 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <!-- Unrolled XHTML+XForms -->
    <p:param type="input" name="xforms"/>
    <!-- Request parameters -->
    <p:param type="input" name="parameters"/>
    <!-- PDF document -->
    <p:param type="output" name="data"/>

    <!-- Get form data if it exists -->
    <!-- This will be the case if somebody has POSTed the data and detail-model.xpl has stored it into the request -->
    <p:processor name="oxf:scope-generator">
        <p:input name="config">
            <config>
                <key>fr-form-data</key>
                <scope>request</scope>
            </config>
        </p:input>
        <p:output name="data" id="request-form-data"/>
    </p:processor>

    <!-- If no request form data found, get form data -->
    <p:choose href="#request-form-data">
        <p:when test="not(/null/@xsi:nil='true')">
            <!-- Data found, just forward -->
            <p:processor name="oxf:identity">
                <p:input name="data" href="#request-form-data"/>
                <p:output name="data" id="form-data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Retrieve data from persistence layer -->
            <!-- This is necessary in the case of PDF template only, because in the other cases the data is loaded by XForms -->
            <p:processor name="oxf:url-generator">
            <p:input name="config" transform="oxf:unsafe-xslt" href="#parameters">
                <config xsl:version="2.0">

                    <!-- Create URI based on properties -->
                    <xsl:variable name="resource"
                                  select="concat(pipeline:property(string-join(('oxf.fr.persistence.app.uri', /*/app, /*/form, 'data'), '.')),
                                            '/crud/', /*/app, '/', /*/form, '/data/', /*/document, '/data.xml')" as="xs:string"/>
                    <url>
                        <xsl:value-of select="pipeline:rewriteServiceURI($resource, true())"/>
                    </url>
                    <!-- Forward the same headers that the XForms engine forwards -->
                    <forward-headers><xsl:value-of select="pipeline:property('oxf.xforms.forward-submission-headers')"/></forward-headers>
                </config>
            </p:input>
            <p:output name="data" id="form-data"/>
        </p:processor>
        </p:otherwise>
    </p:choose>

    <!-- Obtain original form document -->
    <p:processor name="oxf:scope-generator">
        <p:input name="config">
            <config>
                <key>fr-form-definition</key>
                <scope>request</scope>
            </config>
        </p:input>
        <p:output name="data" id="form-document"/>
    </p:processor>

    <!-- Call up persistence layer to obtain the PDF file -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" transform="oxf:unsafe-xslt" href="#form-document">
            <config xsl:version="2.0">
                <url>
                    <xsl:value-of select="pipeline:rewriteServiceURI(//xforms:instance[@id = 'fr-form-attachments']/*/pdf, true())"/>
                </url>
                <!-- Forward the same headers that the XForms engine forwards -->
                <forward-headers><xsl:value-of select="pipeline:property('oxf.xforms.forward-submission-headers')"/></forward-headers>
                <!-- Produce binary so we do our own XML parsing -->
                <mode>binary</mode>
            </config>
        </p:input>
        <p:output name="data" id="pdf-template"/>
    </p:processor>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters/parameter</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Create mapping file -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#form-data"/>
        <p:input name="xhtml" href="#form-document"/>
        <p:input name="request" href="#request"/>
        <p:input name="parameters" href="#parameters"/>
        <p:input name="config" href="print-pdf-template.xsl"/>
        <p:output name="data" id="mapping"/>
    </p:processor>

    <!-- Produce PDF document -->
    <p:processor name="oxf:pdf-template">
        <p:input name="data" href="#form-data"/>
        <p:input name="model" href="#mapping"/>
        <p:input name="template" href="#pdf-template"/>
        <p:output name="data" ref="data"/>
    </p:processor>

    <!-- TODO: example of oxf:add-attribute processor adding content-disposition information -->
    <!-- TODO: build file name dynamically using requested document id? -->
    <!--<p:processor name="oxf:add-attribute">-->
        <!--<p:input name="data" href="#pdf-data"/>-->
        <!--<p:input name="config">-->
            <!--<config>-->
                <!--<match>/*</match>-->
                <!--<attribute-name>content-disposition</attribute-name>-->
                <!--<attribute-value>attachment; filename=form.pdf</attribute-value>-->
            <!--</config>-->
        <!--</p:input>-->
        <!--<p:output name="data" ref="data"/>-->
    <!--</p:processor>-->

</p:config>
