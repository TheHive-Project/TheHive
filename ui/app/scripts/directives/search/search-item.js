(function() {
    'use strict';

    angular.module('theHiveDirectives')
        .directive('searchItem', function($modal, UserInfoSrv) {
            return {
                restrict: 'E',
                replace: true,
                scope: {
                    'value': '=',
                    'type': '@',
                    'icon': '@'
                },
                link: function(scope /*, element, attrs*/ ) {
                    scope.getContentUrl = function() {
                        return 'views/directives/search/' + scope.type + '.html';
                    };
                    scope.isImage = function(contentType) {
                        return angular.isString(contentType) && contentType.indexOf('image') === 0;
                    };
                    scope.showImage = function(attachmentId, attachmentName) {
                        $modal.open({
                            template: '<img style="width:100%" src="/api/datastore/' + attachmentId + '" alt="' + attachmentName + '"></img>',
                            size: 'lg'
                        });
                    };
                    scope.getUserInfo = UserInfoSrv;
                },
                templateUrl: 'views/directives/search/search-item.html'
            };
        });
})();
