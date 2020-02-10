(function() {
    'use strict';

    angular.module('theHiveControllers').controller('OrgListCtrl',
        function($scope, $q, $uibModal, OrganisationSrv, NotificationSrv, appConfig) {
            var self = this;

            self.appConfig = appConfig;

            self.load = function() {
                OrganisationSrv.list()
                    .then(function(response) {
                        self.list = response.data;
                    })
                    .catch(function(err) {
                        NotificationSrv.error('Error', 'Failed to list organisations', err.status);
                    });
            };

            self.showNewOrg = function(mode, org) {
                var modal = $uibModal.open({
                    controller: 'OrgModalCtrl',
                    controllerAs: '$modal',
                    templateUrl: 'views/partials/admin/organisation/list/create.modal.html',
                    size: 'lg',
                    resolve: {
                        organisation: angular.copy(org),
                        mode: function(){
                            return mode;
                        }
                    }
                });

                modal.result
                    .then(function(newOrg) {
                        if (mode === 'edit') {
                            self.update(org.name, newOrg);
                        } else {
                            self.create(newOrg);
                        }
                    })
                    .catch(function(err){
                        if (err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Unable to save the organisation.', err.status);
                        }
                    });
            };

            /**
             * Fetch org links and show a modal to allow selecting the links
             */
            self.showLinks = function(org) {
                var modalInstance = $uibModal.open({
                    //scope: $scope,
                    templateUrl: 'views/partials/admin/organisation/list/link.modal.html',
                    controller: 'OrgLinksModalCtrl',
                    controllerAs: '$modal',
                    resolve: {
                        organisation: function() {
                            return org;
                        },
                        organisations: function() {
                            var list = _.filter(angular.copy(self.list), function(item) {
                                return [OrganisationSrv.defaultOrg, org.name].indexOf(item.name) === -1;
                            });

                            return _.sortBy(list, 'name');
                        },
                        links: function () {
                            return OrganisationSrv.links(org.name);
                        }
                    }
                });

                modalInstance.result.then(function(newLinks) {
                    OrganisationSrv.setLinks(org.name, newLinks)
                        .then(function() {
                            self.load();
                            NotificationSrv.log('Organisation updated successfully', 'success');
                        })
                        .catch(function(err) {
                            NotificationSrv.error('Error', 'Organisation update failed', err.status);
                        });
                });
            };

            self.update = function(orgName, org) {
                OrganisationSrv.update(orgName, _.pick(org, 'name', 'description'))
                    .then(function(/*response*/) {
                        self.load();
                        NotificationSrv.log('Organisation updated successfully', 'success');
                    })
                    .catch(function(err) {
                        NotificationSrv.error('Error', 'Organisation update failed', err.status);
                    });
            };

            self.create = function(org) {
                OrganisationSrv.create(org)
                    .then(function(/*response*/) {
                        self.load();
                        NotificationSrv.log('Organisation created successfully', 'success');
                    })
                    .catch(function(err) {
                        NotificationSrv.error('Error', 'Organisation creation failed', err.status);
                    });
            };

            self.load();
        });
})();
