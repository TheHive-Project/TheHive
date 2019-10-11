(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseSharingCtrl',
        function($scope, $state, $stateParams, $uibModal, $timeout, CaseSrv, CaseTabsSrv, NotificationSrv, organisations, profiles, shares) {
            var self = this;

            this.caseId = $stateParams.caseId;

            this.organisations = organisations;
            this.profiles = profiles;
            this.shares = shares;

            var tabName = 'sharing-' + this.caseId;

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

            this.load = function() {
                return CaseSrv.getShares(this.caseId)
                    .then(function(response) {
                        self.shared = response.data;
                    });
            };


            this.shareCase = function() {

                var modalInstance = $uibModal.open({
                    templateUrl: 'views/partials/case/share/case.share.modal.html',
                    controller: 'CaseShareModalCtrl',
                    controllerAs: '$modal',
                    size: 'max',
                    resolve: {
                        organisations: function() {
                            return _.pluck(self.organisations, 'name');
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

            // PATCH(p"case/share/$id")
            this.removeShare = function(id) {
                CaseSrv.removeShare(id)
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

            this.updateShareProfile = function(id, newValue) {
                CaseSrv.updateShare(id, { profile: newValue })
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
        }
    );
})();
