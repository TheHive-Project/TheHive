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
                    .catch(function() {
                        // TODO: Handle error
                    });
            };

            self.showNewOrg = function(mode, org) {
                var modal = $uibModal.open({
                    controller: 'OrgModalCtrl',
                    controllerAs: '$modal',
                    templateUrl: 'views/partials/admin/organisation/list/create.modal.html',
                    size: 'lg',
                    resolve: {
                        organisation: org,
                        mode: function(){
                            return mode;
                        }
                    }
                });

                modal.result
                    .then(function(org) {
                        if (mode === 'edit') {
                            self.update(org._id, org);
                        } else {
                            self.create(org);
                        }
                    })
                    .catch(function(err){
                        if (!_.isString(err)) {
                            this.NotificationService.error('Unable to save the organisation.');
                        }
                    });
            };

            self.update = function(orgId, org) {
                OrganisationSrv.update(orgId, _.pick(org, 'description'))
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
