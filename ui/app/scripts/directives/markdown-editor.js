(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('markdownEditor', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/directives/markdown-editor.html',
                scope: {
                    content: '=',
                    placeholder: '@',
                    edition: '=?'
                },
                link: function(scope) {
                    scope.edition = true;
                    scope.previewContent = '';

                    scope.edit = function() {
                        scope.edition = true;
                        scope.previewContent = '';
                    };
                    scope.preview = function() {
                        scope.edition = false;
                        scope.previewContent = scope.content;
                    };
                }
            };
        });
})();
