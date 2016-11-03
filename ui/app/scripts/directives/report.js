(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('report', function($templateRequest, $compile) {
            function updateReport(a, b, scope) {
                console.log('update report ' + scope.name);
                if (!angular.isDefined(scope.content) || !angular.isDefined(scope.name)) {
                    console.log('no data, don\'t show anything');
                    scope.element.html('');
                    return;
                }

                // find report template
                $templateRequest(
                        '/api/analyzer/' + scope.name + '/report/' + scope.status.toLowerCase() + '_' + scope.flavor, true)
                    .then(function(tmpl) {
                        scope.element.html($compile(tmpl)(scope));
                    }, function() {
                        scope.element.html('Analyzer not found !');
                    });
            }
            return {
                'restrict': 'E',
                'link': function(scope, element) {
                    scope.element = element;
                    scope.$watchGroup(['name', 'content', 'status'], updateReport);
                },
                'scope': {
                    'name': '=',
                    'artifact': '=',
                    'flavor': '@',
                    'status': '=',
                    'content': '='
                }
            };
        });
})();
