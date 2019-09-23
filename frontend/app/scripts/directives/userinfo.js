(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('userInfo', function(UserSrv) {
            return {
                scope: {
                    user: '=value',
                    field: '@'
                },
                replace: true,
                template: '<span>{{display}}</span>',
                link: function(scope) {
                    scope.display = '';
                    scope.userInfo = UserSrv.getCache;

                    scope.$watch('user', function(value) {
                        if(!value) {
                            return;
                        }
                        scope.userInfo(value).then(function(userData) {
                            if(userData) {
                                scope.display = userData[scope.field];
                            }
                        });
                    });
                }
            };
        });
})();
