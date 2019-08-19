<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>
	<xsl:variable name="properties" select="(
		'xsl:version', 
		'xsl:vendor', 
		'xsl:vendor-url', 
		'xsl:product-name', 
		'xsl:product-version')"/>
	<xsl:template match="/">
		<system-properties>
			<xsl:for-each select="$properties">
				<xsl:variable name="prop" select="."/>
				<xsl:element name="{substring-after($prop, ':')}">
					<xsl:value-of select="system-property($prop)"/>
				</xsl:element>
			</xsl:for-each>
		</system-properties>
	</xsl:template>
</xsl:stylesheet>