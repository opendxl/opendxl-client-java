Command Line Provisioning (Advanced)
====================================

This page contain details regarding the advanced usage of the
``provisionconfig`` operation.

Refer to :doc:`basiccliprovisioning` for basic usage details.

.. _subject-attributes-label:

Routing provisioning operation through a proxy
**********************************************

If the remote call to a provisioning server (ePO or OpenDXL Broker) must be routed through a proxy, then use standard Java system
properties to declare the https proxy host, port, user name, and password. (`<https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html>`_)

For example:

    .. parsed-literal::

        java -Dhttps.proxyHost=proxy.mycompany.com -Dhttps.proxyPort=3128 -Dhttps.proxyUser=proxyUser -Dhttps.proxyPassword=proxyPassword -jar dxlclient-\ |version|\-all.jar provisionconfig config myserver client1


Additional Certificate Signing Request (CSR) Information
********************************************************

Attributes other than the Common Name (CN) may also optionally be provided for
the CSR subject.

For example:

    .. parsed-literal::

        java -jar dxlclient-\ |version|\-all.jar provisionconfig config myserver client1 --country US --state-or-province Oregon --locality Hillsboro --organization Engineering --organizational-unit "DXL Team" --email-address dxl@mcafee.com

    .. note::

        Ensure that the ``-all`` version of the dxlclient ``.jar`` file is specified.


By default, the CSR does not include any Subject Alternative Names. To include
one or more entries of type ``DNS Name``, provide the ``-s`` option.

For example:

    .. parsed-literal::

        java -jar dxlclient-\ |version|\-all.jar provisionconfig config myserver client1 -s client1.myorg.com client1.myorg.net

    .. note::

        Ensure that the ``-all`` version of the dxlclient ``.jar`` file is specified.


Additional Options
******************

The provision operation assumes that the default web server port is 8443,
the default port under which the ePO web interface and OpenDXL Broker Management
Console is hosted.

A custom port can be specified via the ``-t`` option.

For example:

    .. parsed-literal::

        java -jar dxlclient-\ |version|\-all.jar provisionconfig config myserver client1 -t 443

    .. note::

        Ensure that the ``-all`` version of the dxlclient ``.jar`` file is specified.


The provision operation stores each of the certificate artifacts (private key, CSR,
certificate, etc.) with a base name of ``client`` by default. To use an
alternative base name for the stored files, use the ``-f`` option.

For example:

    .. parsed-literal::

        java -jar dxlclient-\ |version|\-all.jar provisionconfig config myserver client1 -f theclient

    .. note::

        Ensure that the ``-all`` version of the dxlclient ``.jar`` file is specified.


The output of the command above should appear similar to the following::

    INFO: Saving csr file to config/theclient.csr
    INFO: Saving private key file to config/theclient.key
    INFO: Saving DXL config file to config/dxlclient.config
    INFO: Saving ca bundle file to config/ca-bundle.crt
    INFO: Saving client certificate file to config/theclient.crt

If the management server's CA certificate is stored in a local CA truststore
file -- one or more PEM-formatted certificates concatenated together into a
single file -- the provision operation can be configured to validate
the management server's certificate against that truststore during TLS session
negotiation by supplying the ``-e`` option.

The name of the truststore file should be supplied along with the option:

    .. parsed-literal::

        java -jar dxlclient-\ |version|\-all.jar config myserver -e config/ca-bundle.crt

    .. note::

        Ensure that the ``-all`` version of the dxlclient ``.jar`` file is specified.


Generating the CSR Separately from Signing the Certificate
**********************************************************

By default, the ``provisionconfig`` command generates a CSR and immediately
sends it to a management server for signing. Certificate generation and signing
could alternatively be performed as separate steps -- for example, to enable a
workflow where the CSR is signed by a certificate authority at a later time.

The ``generatecsr`` operation can be used to generate the CSR and private
key without sending the CSR to the server.

For example:

    .. parsed-literal::

        java -jar dxlclient-\ |version|\-all.jar generatecsr config client1

    .. note::

        Ensure that the ``-all`` version of the dxlclient ``.jar`` file is specified.

The output of the command above should appear similar to the following::

    INFO: Saving csr file to config/client.csr
    INFO: Saving private key file to config/client.key

Note that the ``generatecsr`` operation has options similar to those available
in the ``provisionconfig`` operation for including additional subject attributes
and/or subject alternative names in the generated CSR.

See the :ref:`subject-attributes-label` for more information.

If the ``provisionconfig`` operation includes a ``-r`` option, the
``COMMON_OR_CSRFILE_NAME`` argument is interpreted as the name of a
CSR file to load from disk rather than the Common Name to insert into a new
CSR file.

For example:

    .. parsed-literal::

        java -jar dxlclient-\ |version|\-all.jar provisionconfig config myserver -r config/client.csr

    .. note::

        Ensure that the ``-all`` version of the dxlclient ``.jar`` file is specified.


In this case, the command line output shows that the certificate and
configuration-related files received from the server are stored but no
new private key or CSR file is generated::

    INFO: Saving DXL config file to config/dxlclient.config
    INFO: Saving ca bundle file to config/ca-bundle.crt
    INFO: Saving client certificate file to config/client.crt