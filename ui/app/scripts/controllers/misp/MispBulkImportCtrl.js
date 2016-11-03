(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('MispBulkImportCtrl', function($q, $modalInstance, MispSrv, AlertSrv, events) {
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
                    AlertSrv.log('The selected events have been imported', 'success');

                    $modalInstance.close(_.pluck(response, 'data'));
                }, function(response) {
                    self.disableButtons = false;
                    AlertSrv.error('MispBulkImportCtrl', response.data, response.status);
                });
            };

            self.cancel = function() {
                $modalInstance.dismiss();
            };
        });
})();
