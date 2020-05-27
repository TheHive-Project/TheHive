(function() {
    'use strict';

    angular.module('theHiveControllers').controller('OrgDetailsCtrl',
        function($scope, $q, $uibModal, OrganisationSrv, NotificationSrv, UserSrv, organisation, users, templates, fields, appConfig) {
            var self = this;

            this.org = organisation;
            this.users = users;
            this.templates = templates;
            this.fields = fields;
            this.canChangeMfa = appConfig.config.capabilities.indexOf('mfa') !== -1;

            this.getUserInfo = UserSrv.getCache;

            this.reloadUsers = function() {
                OrganisationSrv.users(self.org.name)
                    .then(function(users) {
                        self.users = users;
                    })
                    .catch(function(err) {
                        NotificationSrv.error('OrgDetailsCtrl', err.data, err.status);
                    });
            };

            this.showUserDialog = function(user) {
                UserSrv.openModal(user, self.org.name)
                    .then(function() {
                        self.reloadUsers();
                    })
                    .catch(function(err) {
                        if (err && !_.isString(err)) {
                            NotificationSrv.error('OrgDetailsCtrl', err.data, err.status);
                        }
                    });
            };
        });
})();
