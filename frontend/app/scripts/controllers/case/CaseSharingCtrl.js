(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseSharingCtrl',
        function($scope, $state, $stateParams, $uibModal, $timeout, ModalSrv, CaseSrv, CaseTabsSrv, NotificationSrv, organisations, profiles, shares) {
            var self = this;

            this.caseId = $stateParams.caseId;

            this.organisations = organisations;
            this.profiles = profiles;
            this.shares = shares;

            var tabName = 'sharing-' + this.caseId;

            this.$onInit = function() {
                // Add tab
                CaseTabsSrv.addTab(tabName, {
                    name: tabName,
                    label: 'Sharing',
                    closable: true,
                    state: 'app.case.sharing',
                    params: {}
                });

                // Select tab
                $timeout(function() {
                    CaseTabsSrv.activateTab(tabName);
                }, 0);

                self.enableAddButton = self.getRemainingOrgs().length > 0;
            };

            this.load = function() {
                return CaseSrv.getShares(this.caseId)
                    .then(function(response) {
                        self.shares = response.data;
                        self.enableAddButton = self.getRemainingOrgs().length > 0;
                    });
            };

            this.getRemainingOrgs = function() {
                var organisationNames = _.pluck(self.organisations, 'name');
                var shareNames = _.pluck(self.shares, 'organisationName');

                return _.filter(organisationNames, function(name) {
                    return shareNames.indexOf(name) === -1;
                });
            };

            this.shareCase = function() {

                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/case/share/case.share.modal.html',
                    controller: 'CaseShareModalCtrl',
                    controllerAs: '$modal',
                    resolve: {
                        organisations: function() {
                            return self.getRemainingOrgs();
                        },
                        profiles: function() {
                            return self.profiles;
                        },
                        shares: function() {
                            return self.shares;
                        }
                    }
                });

                modalInstance.result.then(function(shares) {
                    CaseSrv.setShares(self.caseId, shares)
                        .then(function(/*response*/) {
                            self.load();
                            NotificationSrv.log('Case sharings updated successfully', 'success');
                        })
                        .catch(function(err) {
                            if(err && !_.isString(err)) {
                                NotificationSrv.error('Error', 'Case sharings update failed', err.status);
                            }
                        });
                });
            };

            this.removeShare = function(share) {
                var modalInstance = ModalSrv.confirm(
                    'Remove case share',
                    'Are you sure you want to remove this sharing rule?', {
                        okText: 'Yes, remove it',
                        flavor: 'danger'
                    }
                );

                modalInstance.result
                    .then(function() {
                        return CaseSrv.removeShare(self.caseId, share);
                    })
                    .then(function(/*response*/) {
                        self.load();
                        NotificationSrv.log('Case sharings updated successfully', 'success');
                    })
                    .catch(function(err) {
                        if(err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Case sharings update failed', err.status);
                        }
                    });
            };

            this.updateShareProfile = function(org, profile) {
                CaseSrv.updateShare(org, { profile: profile })
                    .then(function(/*response*/) {
                        self.load();
                        NotificationSrv.log('Case sharings updated successfully', 'success');
                    })
                    .catch(function(err) {
                        self.load();
                        if(err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Case sharings update failed', err.status);
                        }
                    });
            };
        }
    );
})();
