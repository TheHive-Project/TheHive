(function () {
    'use strict';

    angular.module('theHiveControllers').controller('PlatformStatusCtrl', function (ModalSrv, PlatformSrv, NotificationSrv, appConfig) {
        var self = this;

        self.appConfig = appConfig;
        self.indexStatus = {};
        self.checkStats = {};

        self.loading = {
            index: false,
            check: false
        }

        this.loadIndexStatus = function () {
            self.indexStatus = {};
            self.loading.index = true;

            PlatformSrv.getIndexStatus()
                .then(function (response) {
                    self.indexStatus = response.data;
                    self.loading.index = false;
                });
        }

        this.loadCheckStats = function () {
            self.loading.check = true;

            PlatformSrv.getCheckStats()
                .then(function (response) {
                    self.checkStats = response.data;
                    self.loading.check = false;
                })
        }

        this.$onInit = function () {
            self.loadIndexStatus();
            self.loadCheckStats();
        };

        this.exportReport = function () {
            var date = new moment().format('YYYYMMDD-HH:mmZ');
            var fileName = 'Platform-Status-Report-' + date + '.json';

            var content = {
                indexStatus: self.indexStatus,
                checkStatus: self.checkStats,
                schemaStatus: self.appConfig.schemaStatus
            };

            // Create a blob of the data
            var fileToSave = new Blob([JSON.stringify(content)], {
                type: 'application/json',
                name: fileName
            });

            // Save the file
            saveAs(fileToSave, fileName);
        }

        this.reindex = function (indexName) {
            var modalInstance = ModalSrv.confirm(
                'Reindex',
                'Are you sure you want to trigger ' + indexName + ' data reindex', {
                okText: 'Yes, reindex it'
            }
            );

            modalInstance.result
                .then(function () {
                    PlatformSrv.runReindex(indexName);
                })
                .then(function (/*response*/) {
                    NotificationSrv.success('Reindexing of ' + indexName + ' data started sucessfully');
                })
                .catch(function (err) {
                    if (!_.isString(err)) {
                        NotificationSrv.error('Platform status', err.data, err.status);
                    }
                });
        };

        this.rebuildIndex = function (indexName) {
            var modalInstance = ModalSrv.confirm(
                'Drop & Rebuild Index',
                'Are you sure you want to delete and rebuild ' + indexName + ' data reindex. ' +
                'This operation will drop your existing data index and create a new one.', {
                okText: 'Yes, rebuild it',
                flavor: 'danger'
            }
            );

            modalInstance.result
                .then(function () {
                    PlatformSrv.runRebuildIndex(indexName);
                })
                .then(function (/*response*/) {
                    NotificationSrv.success('Rebuild of ' + indexName + ' data started sucessfully');
                })
                .catch(function (err) {
                    if (!_.isString(err)) {
                        NotificationSrv.error('Platform status', err.data, err.status);
                    }
                });
        };

        this.checkControl = function (checkName) {
            var modalInstance = ModalSrv.confirm(
                'Data health check',
                'Are you sure you want to trigger ' + checkName + ' health check', {
                okText: 'Yes, trigger it'
            }
            );

            modalInstance.result
                .then(function () {
                    PlatformSrv.runCheck(checkName);
                })
                .then(function (/*response*/) {
                    NotificationSrv.success('Data health check of ' + checkName + ' started sucessfully');
                })
                .catch(function (err) {
                    if (!_.isString(err)) {
                        NotificationSrv.error('Platform status', err.data, err.status);
                    }
                });
        }

    });
})();
