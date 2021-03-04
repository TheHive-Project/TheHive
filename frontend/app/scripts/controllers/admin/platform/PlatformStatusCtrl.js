(function() {
    'use strict';

    angular.module('theHiveControllers').controller('PlatformStatusCtrl', function(PlatformSrv, NotificationSrv, appConfig) {
            var self = this;

            self.appConfig = appConfig;
            self.indexStatus = {};
            self.checkStats = {};

            self.loading = {
                index: false,
                check: false
            }

            this.loadIndexStatus = function() {
                self.indexStatus = {};
                self.loading.index = true;

                PlatformSrv.getIndexStatus()
                    .then(function(response) {
                        self.indexStatus = response.data;
                        self.loading.index = false;
                    });
            }

            this.loadCheckStats = function() {
                self.loading.check = true;

                PlatformSrv.getCheckStats()
                    .then(function(response) {
                        self.checkStats = response.data;
                        self.loading.check = false;
                    })
            }

            this.$onInit = function() {
                self.loadIndexStatus();
                self.loadCheckStats();
            };

            this.exportReport = function() {
                var date = new moment().format('YYYYMMDD-HH:mmZ');
                var fileName = 'Platform-Status-Report-'+date+'.json';


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

            this.reindex = function(indexName) {
                PlatformSrv.runReindex(indexName)
                    .then(function(response) {
                        NotificationSrv.log('Reindexing of ' + indexName + ' started sucessfully', 'success');
                    });
            };

            this.checkControl = function(checkName) {
                PlatformSrv.runCheck(checkName)
                    .then(function(response) {
                        NotificationSrv.log('Integrity check of ' + checkName + ' started sucessfully', 'success');
                    });
            }

        });
})();
