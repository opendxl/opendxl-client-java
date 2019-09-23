# OpenDXL Java Client

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.opendxl/dxlclient/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.opendxl/dxlclient)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://travis-ci.org/opendxl/opendxl-client-java.png?branch=master)](https://travis-ci.org/opendxl/opendxl-client-java)

## Overview

The OpenDXL Java Client enables the development of applications that connect to the [McAfee Data Exchange Layer](http://www.mcafee.com/us/solutions/data-exchange-layer.aspx) messaging fabric for the purposes of sending/receiving events and invoking/providing services.

## Documentation

See the [Wiki](https://github.com/opendxl/opendxl-client-java/wiki) for an overview of the Data Exchange Layer (DXL), the OpenDXL Java client, and samples.

See the [Java Client SDK Documentation](https://opendxl.github.io/opendxl-client-java/docs) for installation instructions, API documentation, and samples.

## Installation

To start using the OpenDXL Java client:

* Download the [Latest Release](https://github.com/opendxl/opendxl-client-java/releases/latest)
* Extract the release .zip (Windows) or .tar (Linux) file
* View the `README.html` file located at the root of the extracted files.
  * The `README` links to the SDK documentation which includes installation instructions, API details, and samples.
  * The SDK documentation is also available on-line [here](https://opendxl.github.io/opendxl-client-java/docs).

## Maven Repository

Visit the [OpenDXL Java Client Maven Repository](https://search.maven.org/artifact/com.opendxl/dxlclient) for
access to all released versions including the appropriate dependency syntax for a large number of management 
systems (Maven, Gradle, SBT, Ivy, Grape, etc.).

Maven:

```xml
<dependency>
  <groupId>com.opendxl</groupId>
  <artifactId>dxlclient</artifactId>
  <version>0.2.3</version>
</dependency>
```
or Gradle:
```groovy
compile 'com.opendxl:dxlclient:0.2.3'
```

## Bugs and Feedback

For bugs, questions and discussions please use the [Github Issues](https://github.com/opendxl/opendxl-client-java/issues).

## LICENSE

Copyright 2018 McAfee, LLC

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
