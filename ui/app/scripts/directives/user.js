(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('user', function(UserInfoSrv) {
            return {
                scope: {
                    user: '=userId',
                    iconOnly: '@',
                    iconSize: '@'
                },
                templateUrl: 'views/directives/user.html',
                link: function(scope) {                    
                    scope.userInfo = UserInfoSrv;
                    scope.userData = {};

                    scope.$watch('user', function(value) {
                        scope.userData = scope.userInfo.get(value);
                    });
                }
            };
        });
})();
