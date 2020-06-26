(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('updatableUser', function(UserSrv, UtilsSrv, AuthenticationSrv, NotificationSrv) {
            return {
                restrict: 'E',
                link: function(scope, element, attrs, ctrl, transclude) {
                    var cached = false;

                    UtilsSrv.updatableLink(scope, element, attrs, ctrl, transclude);

                    scope.setValue = function(value) {
                        scope.value = value;
                    };
                    scope.getUserInfo = UserSrv.getCache;

                    scope.$watch('updatable.updating', function(value) {

                        if(value === true && !cached) {
                            // TODO nadouani use {"_field": "locked": "_value": false}
                            UserSrv.list(AuthenticationSrv.currentUser.organisation, {
                                filter: { locked: false },
                                sort: [{'name': 'asc'}]
                            })
                                .then(function(users) {
                                    scope.userList = users;
                                })
                                .catch(function(err) {
                                    NotificationSrv.error('Fetching users', err.data, err.status);
                                });
                            cached = true;
                        }
                    });
                },
                templateUrl: 'views/directives/updatable-user.html',
                scope: {
                    'value': '=?',
                    'onUpdate': '&',
                    'active': '=?'
                }
            };
        });
})();
