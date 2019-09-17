(function() {
    'use strict';

    angular.module('theHiveControllers').controller('OrgDetailsCtrl',
        function($scope, $q, OrganisationSrv, UserInfoSrv, organisation) {
            var self = this;

            this.org = organisation;
            this.getUserInfo = UserInfoSrv;
        });
})();
