(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('ifPermission', function(AuthenticationSrv) {
        return {
            restrict: 'A',
            scope: false,
            link: function(scope, element, attrs) {
                var permissions = _.map((attrs.ifPermission || '').split(','), function(item){
                    return s.trim(item);
                });

                console.log(attrs);
                console.log('Checking permissions: ' + attrs.ifPermission);

                if(!AuthenticationSrv.hasPermission(permissions)){
                    element.remove();
                }
            }
        };
    });
})();
