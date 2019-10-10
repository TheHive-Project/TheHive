(function() {
    'use strict';

    angular.module('theHiveControllers')
        .controller('CaseShareModalCtrl', function($uibModalInstance, organisations, profiles, shares) {
            this.organisations = organisations;
            this.profiles = profiles;
            this.shares = shares;

            this.rules = [];

            this.options = ['all', 'none'];

            this.addRule = function() {
                this.rules.push({
                    organisations: [],
                    profile: null,
                    tasks: 'none',
                    observables: 'none'
                });
            };

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };

            this.save = function() {
                var shares = [];

                _.each(this.rules, function(rule) {

                    _.each(rule.organisations, function(org) {
                        shares.push({
                            organisationName: org,
                            profile: rule.profile,
                            tasks: rule.tasks,
                            observables: rule.observables
                        });
                    });

                });

                $uibModalInstance.close(shares);
            };
        });
})();
