# Build from sources

This document is a step-by-step guide to build TheHive from sources.

## 1. Pre-requisites

The following softwares are required to download and build TheHive.

* Java Development Kit 8 (JDK)
  * downloadable from http://www.oracle.com/technetwork/java/javase/downloads/index.html
* git
  * Use the system package or downloadable it from http://www.git-scm.com/downloads
* ElasticSearch 2.3
  * downloadable from https://www.elastic.co/downloads/past-releases/elasticsearch-2-3-5
* NodeJs with its package manager (NPM)
  * downloadable from https://nodejs.org/en/download/
* Grunt
  * After NodeJs installation, run `sudo npm install -g grunt-cli`
* Bower
  * After NodeJs installation, run `sudo npm install -g bower`


# 2. Quick Build Guide

To install the requirements and build TheHive from sources, please follow the instructions below depending on your operating system.

## 2.1. CentOS/RHEL

### 2.1.1. Packages

```
sudo yum -y install git bzip2
```

### 2.1.2. Installation of OpenJDK

```
sudo yum -y install java-1.8.0-openjdk-devel
```

### 2.1.3. Installation of ElasticSearch

Download and install the public signing key:

```
sudo rpm --import https://packages.elastic.co/GPG-KEY-elasticsearch
```

Add the following in your `/etc/yum.repos.d/` directory in a file with a `.repo` suffix, for example `elasticsearch.repo`:

```
cat << __EOF | sudo tee /etc/yum.repos.d/elasticsearch.repo
[elasticsearch-2.x]
name=Elasticsearch repository for 2.x packages
baseurl=https://packages.elastic.co/elasticsearch/2.x/centos
gpgcheck=1
gpgkey=https://packages.elastic.co/GPG-KEY-elasticsearch
enabled=1
__EOF
```

Your repository is ready for use. You can install ElasticSearch with:
```
sudo yum -y install elasticsearch
```

### 2.1.4. Installation of NodeJs

Install the EPEL Repository:

You should have the "extras" repository enabled, then:  
```
sudo yum -y install epel-release
```

Then, you can install NodeJs:

```
sudo yum -y install nodejs
```

### 2.1.5. Installation of bower and grunt

```
sudo npm install -g bower grunt-cli
```

## 2.2. Ubuntu

### 2.2.1. Packages

```
sudo apt-get install git wget
```

### 2.2.2. Installation of Oracle JDK

```
echo 'deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main' | sudo tee -a /etc/apt/sources.list.d/java.list
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-key EEA14886
sudo apt-get update
sudo apt-get install oracle-java8-installer
```

### 2.2.3. Installation of ElasticSearch

```
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-key D88E42B4
echo "deb https://packages.elastic.co/elasticsearch/2.x/debian stable main" | sudo tee -a /etc/apt/sources.list.d/elasticsearch-2.x.list
sudo apt-get update && sudo apt-get install elasticsearch
```

### 2.2.4. Installation of NodeJs

```
sudo apt-get install wget
wget -qO- https://deb.nodesource.com/setup_4.x | sudo bash -
sudo apt-get install nodejs
```

### 2.2.5. Installation of bower and grunt

```
sudo npm install -g bower grunt-cli
```

## 2.3. TheHive

### Download sources

```
git clone https://github.com/CERT-BDF/TheHive.git
```

### Build the projects

```
cd TheHive
bin/activator clean stage
```

It will download all dependencies (could be long) then build the back-end.
This command clean previous build files and create an autonomous package in `target/universal/stage` directory. This packages contains TheHive binaries with required libraries (`/lib`), analyzers (`/analyzers`), configuration files (`/conf`) and startup scripts (`/bin`).

Binaries are built and stored in `TheHive/target/universal/stage/`. Install them in `/opt/thehive` for example.

```
sudo cp -r TheHive/target/universal/stage /opt/thehive
```

Follow the [configuration part of the installation guide](Installation-guide#42-configuration) to run TheHive.


### Configure and start elasticsearch

Edit `/etc/elasticsearch/elasticsearch.yml` and add the following lines:

```
network.host: 127.0.0.1
script.inline: on
cluster.name: hive
threadpool.index.queue_size: 100000
threadpool.search.queue_size: 100000
threadpool.bulk.queue_size: 1000
```

Start the service:

```
service elasticsearch restart
```


### First start

Follow [4.3. First start in the Installation guide](Installation-guide#43-first-start) to start using TheHive.


## Build the front-end only
Building back-end builds also front-end, so you don't need to build front-end separately. This section is useful only for troubleshooting or in order to install front-end in a reverse proxy.

Go to front-end directory:
```
cd TheHive/ui
```

Install NodeJs libraries (required by building step), bower libraries (javascript libraries downloaded by browser). Then build the front-end :
```
npm install
bower install
grunt build
```

This step generates static files (html, javascript and related resources) in `dist` directory. These files are ready to be imported in http server.
