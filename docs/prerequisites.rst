SDK Overview and Prerequisites
==============================

Prerequisites
*************

* DXL Brokers (3.0.1 or later) deployed within an ePO managed environment
* DXL Extensions (3.0.1 or later)
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

Visit the `OpenDXL Java Client Maven Repository <https://mvnrepository.com/artifact/com.opendxl/dxlclient>`_
access to all released versions including the appropriate dependency syntax for a large number of management
systems (Maven, Gradle, SBT, Ivy, Grape, etc.).

Maven:

    .. code-block:: xml

        <dependency>
          <groupId>com.opendxl</groupId>
          <artifactId>dxlclient</artifactId>
          <version>0.1.0</version>
        </dependency>
or Gradle:

    .. code-block:: groovy

        compile 'com.opendxl:dxlclient:0.1.0'