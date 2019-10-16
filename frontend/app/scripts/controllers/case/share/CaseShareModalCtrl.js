(function() {
    'use strict';

    angular.module('theHiveControllers')
        .controller('CaseShareModalCtrl', function($uibModalInstance, organisations, profiles, shares) {
            var self = this;

            this.organisations = organisations;
            this.profiles = profiles;
            this.shares = shares;

            this.formData = {
                organisations: [],
                profile: null,
                tasks: 'none',
                observables: 'none'
            };

            this.options = ['all', 'none'];

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };

            this.save = function() {
                var shares = [];

                _.each(self.formData.organisations, function(org) {
                    shares.push({
                        organisationName: org,
                        profile: self.formData.profile,
                        tasks: self.formData.tasks,
                        observables: self.formData.observables
                    });
                });

                $uibModalInstance.close(shares);
            };
        });
})();
