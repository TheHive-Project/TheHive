(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('MispEventCtrl', function($rootScope, $state, $modalInstance, MispSrv, AlertSrv, event) {
            var self = this;
            var eventId = event.id;

            self.loading = true;

            self.pagination = {
                pageSize: 10,
                currentPage: 1,
                data: []
            };

            self.loadPage = function() {
                var end = self.pagination.currentPage * self.pagination.pageSize;
                var start = end - self.pagination.pageSize;

                var data = [];
                angular.forEach(self.event.attributes.slice(start, end), function(d) {
                    data.push(d);
                });

                self.pagination.data = data;
            };

            self.load = function() {
                MispSrv.get(eventId).then(function(response) {
                    self.event = response.data;
                    self.loading = false;

                    self.dataTypes = _.countBy(self.event.attributes, function(attr) {
                        return attr.dataType;
                    });

                    self.loadPage();
                });
            };

            self.import = function() {
                self.loading = true;
                MispSrv.create(self.event.id).then(function(response) {
                    $modalInstance.dismiss();

                    $rootScope.$broadcast('misp:event-imported');

                    $state.go('app.case.details', {
                        caseId: response.data.id
                    });
                }, function(response) {
                    self.loading = false;
                    AlertSrv.error('MispEventCtrl', response.data, response.status);
                });
            };

            self.ignore = function(){
                MispSrv.ignore(self.event.id).then(function( /*data*/ ) {
                    $modalInstance.dismiss();                    
                });
            };

            self.follow = function() {
                var fn = angular.noop;

                if (self.event.follow === true) {
                    fn = MispSrv.unfollow;
                } else {
                    fn = MispSrv.follow;
                }

                fn(self.event.id).then(function() {
                    self.load();
                }).catch(function(response) {
                    AlertSrv.error('MispEventCtrl', response.data, response.status);
                });
            };

            self.cancel = function() {
                $modalInstance.dismiss();
            };

            self.load();
        });
})();
