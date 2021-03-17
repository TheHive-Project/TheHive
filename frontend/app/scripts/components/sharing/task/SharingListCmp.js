(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('taskSharingList', {
            controller: function() {
                var self = this;

                this.remove = function(share) {
                    this.onDelete({
                        share: share
                    });
                };

                this.updateProfile = function(org, newProfile) {
                    this.onUpdateProfile({
                        profile: newProfile,
                        org: org
                    });
                };

                this.requireAction = function(org) {
                    this.onRequireAction({
                        task: self.task,
                        org: org
                    });
                };

                this.cancelRequireAction = function(org) {
                    this.onCancelRequireAction({
                        task: self.task,
                        org: org
                    });
                }
            },
            controllerAs: '$ctrl',
            templateUrl: 'views/components/sharing/task/sharing-list.html',
            bindings: {
                task: '<',
                shares: '<',
                organisations: '<',
                profiles: '<',
                readOnly: '<',
                //onReload: '&',
                onUpdateProfile: '&',
                onDelete: '&',
                onRequireAction: '&',
                onCancelRequireAction: '&',
                permissions: '='
            }
        });
})();
