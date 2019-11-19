(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('ifPermission', function(AuthenticationSrv, SecuritySrv) {
        return {
            restrict: 'A',
            scope: false,
            link: function(scope, element, attrs) {
                var requiredPermissions = _.map((attrs.ifPermission || '').split(','), function(item){
                    return s.trim(item);
                });

                if(attrs.allowed !== undefined) {
                    // Check the list of specified allowed permissions
                    if(!SecuritySrv.checkPermissions(requiredPermissions, attrs.allowed)) {
                        element.remove();
                    }
                } else if(!AuthenticationSrv.hasPermission(requiredPermissions)){
                    // Check the user defined permissions
                    element.remove();
                }
            }
        };
    });
})();
