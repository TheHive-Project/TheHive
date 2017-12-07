(function() {
    'use strict';

    angular
        .module('theHiveControllers')
        .controller('CaseExportDialogCtrl', function(MispSrv, NotificationSrv, clipboard, $uibModalInstance, caze, config) {
            var self = this;

            this.caze = caze;
            this.mode = '';
            this.servers = config.servers;
            this.failures = [];

            this.existingExports = {};
            this.loading = false;

            _.each(_.filter(this.caze.stats.alerts || [], function(item) {
                return item.type === 'misp';
            }), function(item) {
                self.existingExports[item.source] = true;
            });

            var extractExportErrors = function (errors) {
                var result = [];

                result = errors.map(function(item) {
                    return {
                        data: item.object.dataType === 'file' ? item.object.attachment.name : item.object.data,
                        message: item.message
                    };
                });

                return result;
            }

            this.copyToClipboard = function() {
                clipboard.copyText(_.pluck(self.failures, 'data').join('\n'));
                $uibModalInstance.dismiss();
            }

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };

            this.confirm = function() {
                $uibModalInstance.close();
            };

            this.export = function(server) {
                self.loading = true;
                self.failures = [];
                
                MispSrv.export(self.caze.id, server.name)
                .then(function(response){
                    var success = 0,
                        failure = 0;

                    if (response.status === 207) {
                        success = response.data.success.length;
                        failure = response.data.failure.length;

                        self.mode = 'error';
                        self.failures = extractExportErrors(response.data.failure);

                        NotificationSrv.log('The case has been successfully exported, but '+ failure +' observable(s) failed', 'warning');
                    } else {
                        success = angular.isObject(response.data) ? 1 : response.data.length;
                        NotificationSrv.log('The case has been successfully exported with ' + success+ ' observable(s)', 'success');
                        $uibModalInstance.close();
                    }
                    self.loading = false;

                }, function(err) {
                    if(!err) {
                        return;
                    }

                    if (err.status === 400) {
                        self.mode = 'error';
                        self.failures = extractExportErrors(err.data);
                    } else {
                        NotificationSrv.error('CaseExportCtrl', 'An unexpected error occurred while exporting case', err.status);
                    }
                    self.loading = false;
                });
            }
        });
})();
