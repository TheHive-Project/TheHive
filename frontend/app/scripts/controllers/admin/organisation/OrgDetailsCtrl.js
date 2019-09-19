(function() {
    'use strict';

    angular.module('theHiveControllers').controller('OrgDetailsCtrl',
        function($scope, $q, OrganisationSrv, UserInfoSrv, organisation, users) {
            var self = this;

            this.org = organisation;
            this.users = users;

            this.getUserInfo = UserInfoSrv;

        });
})();
