SDK Overview and Prerequisites
==============================

Prerequisites
*************

* One or more DXL Brokers:
 - `OpenDXL Broker <https://github.com/opendxl/opendxl-broker>`_
 - DXL Brokers (3.0.1 or later) deployed within an ePO managed environment
* Java Development Kit 8 (JDK 8) or later

Java SDK Contents
*****************

.. tabularcolumns:: |{5cm}|{5cm}|{5cm}|

+--------------+----------------------------------+-------------------------------------------------------------------+
| Directory    | File                             |  Description                                                      |
+--------------+----------------------------------+-------------------------------------------------------------------+
| doc          |                                  | Documentation for the Java Client SDK.                            |
+--------------+----------------------------------+-------------------------------------------------------------------+
| lib          |                                  | Directory containing the DXL client libraries.                    |
+--------------+----------------------------------+-------------------------------------------------------------------+
|              | dxlclient-\ |version|\-all.jar   | The ``all`` version of the DXL client library.                    |
|              |                                  |                                                                   |
|              |                                  | This library includes all classes from the required third party   |
|              |                                  | libraries in a single ``.jar`` file.                              |
|              |                                  |                                                                   |
|              |                                  | This version of the library should be                             |
|              |                                  | used with the Java ``-jar`` argument to execute the DXL client    |
|              |                                  | command line (for provisioning the client, etc.).                 |
+--------------+----------------------------------+-------------------------------------------------------------------+
|              | dxlclient-\ |version|\.jar       | The standard version of the DXL client library (does not include  |
|              |                                  | classes from third party libraries).                              |
+--------------+----------------------------------+-------------------------------------------------------------------+
| lib/3rdparty |                                  | Directory containing the third party libraries that are           |
|              |                                  | required by the DXL client library.                               |
+--------------+----------------------------------+-------------------------------------------------------------------+
| sample       |                                  | Directory containing samples used to test the DXL client.         |
+--------------+----------------------------------+-------------------------------------------------------------------+

Maven Repository
****************

Visit the `OpenDXL Java Client Maven Repository <https://search.maven.org/artifact/com.opendxl/dxlclient>`_ for
access to all released versions including the appropriate dependency syntax for a large number of management
systems (Maven, Gradle, SBT, Ivy, Grape, etc.).

Maven:

    .. code-block:: xml

        <dependency>
          <groupId>com.opendxl</groupId>
          <artifactId>dxlclient</artifactId>
          <version>0.1.2</version>
        </dependency>
or Gradle:

    .. code-block:: groovy

        compile 'com.opendxl:dxlclient:0.1.2'
