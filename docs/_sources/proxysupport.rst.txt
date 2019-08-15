Proxy Support
=============

To have the OpenDXL Java Client connect to DXL Brokers via a proxy, set the proxy host name or IP address, port,
user name, and password in the ``dxlclient.config`` file under the ``[Proxy]`` section. The ``[Proxy]``
section is optional and if it does not exist or the values under it are blank, then the
OpenDXL Java Client will not use a proxy when connecting to DXL Brokers.  The ``User`` and ``Password`` settings are
only required if the proxy requires authentication.

       .. parsed-literal::

          [General]
          UseWebSockets=true

          [Certs]
          BrokerCertChain=c:\\certificates\\brokercerts.crt
          CertFile=c:\\certificates\\client.crt
          PrivateKey=c:\\certificates\\client.key

          [Brokers]
          {5d73b77f-8c4b-4ae0-b437-febd12facfd4}={5d73b77f-8c4b-4ae0-b437-febd12facfd4};8883;mybroker.mcafee.com;192.168.1.12
          {24397e4d-645f-4f2f-974f-f98c55bdddf7}={24397e4d-645f-4f2f-974f-f98c55bdddf7};8883;mybroker2.mcafee.com;192.168.1.13

          [BrokersWebSockets]
          {5d73b77f-8c4b-4ae0-b437-febd12facfd4}={5d73b77f-8c4b-4ae0-b437-febd12facfd4};443;mybroker.mcafee.com;192.168.1.12
          {24397e4d-645f-4f2f-974f-f98c55bdddf7}={24397e4d-645f-4f2f-974f-f98c55bdddf7};443;mybroker2.mcafee.com;192.168.1.13

          [Proxy]
          Address=proxy.mycompany.com
          Port=3128
          User=proxyUser
          Password=proxyPassword
