(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('ifNotPermission', function(AuthenticationSrv, SecuritySrv) {
        return {
            restrict: 'A',
            scope: false,
            link: function(scope, element, attrs) {
                var restrictedPermissions = _.map((attrs.ifNotPermission || '').split(','), function(item){
                    return s.trim(item);
                });

                if(attrs.allowed !== undefined) {
                    // Check the list of specified allowed permissions
                    if(SecuritySrv.checkPermissions(restrictedPermissions, attrs.allowed)) {
                        element.remove();
                    }
                } else if(AuthenticationSrv.hasPermission(restrictedPermissions)){
                    // Check the user defined permissions
                    element.remove();
                }
            }
        };
    });
})();
