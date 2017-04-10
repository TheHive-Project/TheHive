(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('MispBulkImportCtrl', function($rootScope, $q, $uibModalInstance, MispSrv, NotificationSrv, events) {
            var self = this;
            self.events = events;
            self.disableButtons = false;

            self.import = function() {
                self.disableButtons = true;

                var ids = _.pluck(self.events, 'id');

                var promises = _.map(ids, function(id) {
                    return MispSrv.create(id);
                });

                $q.all(promises).then(function(response) {
                    NotificationSrv.log('The selected events have been imported', 'success');

                    $rootScope.$broadcast('misp:event-imported');

                    $uibModalInstance.close(_.pluck(response, 'data'));
                }, function(response) {
                    self.disableButtons = false;
                    NotificationSrv.error('MispBulkImportCtrl', response.data, response.status);
                });
            };

            self.cancel = function() {
                $uibModalInstance.dismiss();
            };
        });
})();
