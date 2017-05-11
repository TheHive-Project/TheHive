# Installation Guide for Ubuntu 16.04 LTS

This guide describes the manual installation of TheHive from binaries in Ubuntu 16.04.

# 1. Minimal Ubuntu Installation

Install a minimal Ubuntu 16.04  system with the following software:
 * Java runtime environment 1.8+ (JRE)
 * ElasticSearch 2.x

Make sure your system is up-to-date:

```
sudo apt-get update
sudo apt-get upgrade
```

# 2. Install a Java Virtual Machine
You can install either Oracle Java or OpenJDK.

## 2.1. Oracle Java
```
echo 'deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main' | sudo tee -a /etc/apt/sources.list.d/java.list
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-key EEA14886
sudo apt-get update
sudo apt-get install oracle-java8-installer
```

Once Oracle Java is installed, go directly to section
[3. Install and Prepare your Database](#3-install-and-prepare-your-database).

## 2.2 OpenJDK
```
sudo add-apt-repository ppa:openjdk-r/ppa
sudo apt-get update
sudo apt-get install openjdk-8-jre-headless

```

# 3. Install ElasticSearch

Installation of ElasticSearch is describe in the following [guide](elasticsearch-guide.md)
 
# 4. Install TheHive

Binary package can be downloaded at [thehive-latest.zip](https://dl.bintray.com/cert-bdf/thehive/thehive-latest.zip)

After configuring TheHive, if you use Cortex, don't forget to install
[report templates](../admin-guid.md#3-report-template-management).

## 4.1. Install from Binaries

Download and unzip the chosen binary package. TheHive files can be installed wherever you want on the filesystem. In
this guide, we decided to set it in `/opt`.

```
cd /opt
wget https://dl.bintray.com/cert-bdf/thehive/thehive-latest.zip
unzip thehive-latest.zip
ln -s thehive-x.x.x thehive
```

### 4.2. Configuration

#### 4.2.1 Required configuration

Please refer the [configuration guide](../admin/configuration.md) for full information on TheHive configuration.
The only required parameter in order to start TheHive is the key of the server (`play.crypto.secret`). This key is used
to authenticate cookies that contain data. If TheHive runs in cluster mode, all instance must share the same key.
You can generate the minimal configuration with the following command lines (they assume that you have created a
dedicated user for TheHive, named thehive):

```
sudo mkdir /etc/thehive
(cat << _EOF_
# Secret key
# ~~~~~
# The secret key is used to secure cryptographics functions.
# If you deploy your application to several instances be sure to use the same key!
play.crypto.secret="$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 64 | head -n 1)"
_EOF_
) | sudo tee -a /etc/thehive/application.conf
```

Now you can start TheHive.

For advanced configuration, please, refer to the [configuration page](../admin/configuration.md) and default configuration
information you can find [here](../admin/default-configuration.md). You will especially find all the necessary information to
connect TheHive with Cortex and MISP.


### 4.3. First start

Change your current directory to TheHive installation directory (`/opt/thehive` in this guide), then execute:
```
bin/thehive -Dconfig.file=/etc/thehive/application.conf
```

It is recommended to use a dedicated non-privilege user to start TheHive. If so, make sure that your user can create log
file in `/var/log/thehive/`

This command starts an HTTP service on port 9000/tcp. You can change the port by adding "http.port=8080" in the
configuration file or add the "-Dhttp.port=8080" parameter to the command line. If you run TheHive using a
non-privileged user, you can't bind a port under 1024.


If you'd rather start the application as a service, do the following:
```
sudo addgroup thehive
sudo adduser --system thehive
sudo cp /opt/thehive/package/thehive.service /usr/lib/systemd/system
sudo chown -R thehive:thehive /opt/thehive
sudo chown thehive:thehive /etc/thehive/application.conf
sudo chmod 640 /etc/thehive/application.conf
sudo systemctl enable thehive
sudo service thehive start
```

Please note that the service may take some time to start.

Then open your browser and connect to http://YOUR_SERVER_ADDRESS:9000/

The first time you connect you will have to create the database schema. Click "Migrate database" to create the DB schema.

![](../files/installguide_update_database.png)

Once done, you should be redirected to the page for creating the administrator's account.

![](../files/installguide_create_admin.png)

Once created, you should be redirected to the login page.

![](../files/installguide_login.png)

**Warning**: at this stage, if you missed the creation of the admin user, you will not be able to do it unless you
delete the index in ElasticSearch. In the case you made a mistake, just delete the index with the following command
(beware, it deletes everything in the database)
```
curl -X DELETE http://127.0.0.1:9200/the_hive_9
```

And reload the page or restart TheHive.

## 5. Update

To update TheHive from binaries, just stop the service, download the latest package, rebuild the link `/opt/thehive` and
restart the service.

```
service thehive stop
cd /opt
wget https://dl.bintray.com/cert-bdf/thehive/thehive-latest.zip
unzip thehive-latest.zip
rm /opt/thehive && ln -s thehive-x.x.x thehive
chown -R thehive:thehive /opt/thehive /opt/thehive-x.x.x
service thehive start
```
