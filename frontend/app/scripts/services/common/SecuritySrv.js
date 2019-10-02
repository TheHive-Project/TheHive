(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('SecuritySrv', function() {

            this.checkPermissions = function(allowedPermissions, permissions) {
                if(_.isString(permissions)) {
                    permissions = permissions.split(',') || [];
                }
                return !_.isEmpty(_.intersection(allowedPermissions, permissions));
            };

        });
})();
