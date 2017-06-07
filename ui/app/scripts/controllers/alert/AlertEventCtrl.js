(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('AlertEventCtrl', function($scope, $rootScope, $state, $uibModalInstance, CaseResolutionStatus, AlertingSrv, NotificationSrv, event) {
            var self = this;
            var eventId = event.id;

            self.CaseResolutionStatus = CaseResolutionStatus;
            self.event = event;

            self.loading = true;

            self.pagination = {
                pageSize: 10,
                currentPage: 1,
                filter: '',
                data: []
            };
            self.filteredArtifacts = [];

            this.filterArtifacts = function(value) {
                self.pagination.currentPage = 1;
                this.pagination.filter= value;
                this.loadPage();
            };

            self.loadPage = function() {
                var end = self.pagination.currentPage * self.pagination.pageSize;
                var start = end - self.pagination.pageSize;

                self.filteredArtifacts = (self.pagination.filter === '' ? self.event.artifacts : _.filter(self.event.artifacts, function(item) {
                    return item.dataType === self.pagination.filter;
                })) || [];

                var data = [];
                angular.forEach(self.filteredArtifacts.slice(start, end), function(d) {
                    data.push(d);
                });

                self.pagination.data = data;
            };

            self.load = function() {
                AlertingSrv.get(eventId).then(function(response) {
                    self.event = response.data;
                    self.loading = false;

                    self.dataTypes = _.countBy(self.event.artifacts, function(attr) {
                        return attr.dataType;
                    });

                    self.loadPage();
                }, function(response) {
                  self.loading = false;
                  NotificationSrv.error('AlertEventCtrl', response.data, response.status);
                  $uibModalInstance.dismiss();
                });
            };

            self.import = function() {
                self.loading = true;
                AlertingSrv.create(self.event.id).then(function(response) {
                    $uibModalInstance.dismiss();

                    $rootScope.$broadcast('alert:event-imported');

                    $state.go('app.case.details', {
                        caseId: response.data.id
                    });
                }, function(response) {
                    self.loading = false;
                    NotificationSrv.error('AlertEventCtrl', response.data, response.status);
                });
            };

            this.follow = function() {
                var fn = angular.noop;

                if (self.event.follow === true) {
                    fn = AlertingSrv.unfollow;
                } else {
                    fn = AlertingSrv.follow;
                }

                fn(self.event.id).then(function() {
                    $uibModalInstance.dismiss();
                }).catch(function(response) {
                    NotificationSrv.error('AlertEventCtrl', response.data, response.status);
                });
            };

            this.canMarkAsRead = AlertingSrv.canMarkAsRead;
            this.canMarkAsUnread = AlertingSrv.canMarkAsUnread;

            this.markAsRead = function() {
                var fn = angular.noop;

                if(this.canMarkAsRead(this.event)) {
                    fn = AlertingSrv.markAsRead;
                } else {
                    fn = AlertingSrv.markAsUnread;
                }

                fn(this.event.id).then(function( /*data*/ ) {
                    $uibModalInstance.dismiss();
                }, function(response) {
                    NotificationSrv.error('AlertEventCtrl', response.data, response.status);
                });
            };

            self.cancel = function() {
                $uibModalInstance.dismiss();
            };

            self.load();
        });
})();
