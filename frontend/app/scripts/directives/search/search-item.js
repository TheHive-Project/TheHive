(function() {
    'use strict';

    angular.module('theHiveDirectives')
        .directive('searchItem', function($uibModal, HtmlSanitizer, UserSrv) {
            return {
                restrict: 'E',
                replace: true,
                scope: {
                    'value': '=',
                    'type': '@',
                    'icon': '@',
                    onTitleClicked: '&'
                },
                link: function(scope /*, element, attrs*/ ) {
                    scope.getContentUrl = function() {
                        return 'views/directives/search/' + scope.type + '.html';
                    };
                    scope.isImage = function(contentType) {
                        return angular.isString(contentType) && contentType.indexOf('image') === 0;
                    };
                    scope.showImage = function(attachmentId, attachmentName) {
                        $uibModal.open({
                            template: '<img style="width:100%" src="./api/datastore/' + HtmlSanitizer.sanitize(attachmentId) + '" alt="' + HtmlSanitizer.sanitize(attachmentName) + '"></img>',
                            size: 'lg'
                        });
                    };
                    scope.getUserInfo = UserSrv.getCache;
                },
                templateUrl: 'views/directives/search/search-item.html'
            };
        });
})();
