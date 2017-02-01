(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('user', function(UserInfoSrv) {
            return {
                scope: {
                    user: '=userId',
                    iconOnly: '=',
                    iconSize: '@'
                },
                templateUrl: 'views/directives/user.html',
                link: function(scope) {
                    scope.userInfo = UserInfoSrv;
                    scope.initials = '';

                    scope.$watch('userData.name', function(value) {
                        if(!value) {
                            return;
                        }

                        scope.initials = value.split(' ')
                            .map(function(item) {
                                return item[0];
                            })
                            .join('')
                            .substr(0, 3)
                            .toUpperCase();
                    });

                    scope.$watch('user', function(value) {
                        scope.userData = scope.userInfo.get(value);

                    });
                }
            };
        });
})();
