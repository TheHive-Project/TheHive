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
                //$templateRequest('/api/analyzer/' + scope.name + '/report/' + scope.status.toLowerCase() + '_' + scope.flavor, true)
                $templateRequest('/api/connector/cortex/report/template/content/' + scope.name + '/' + scope.flavor, true)
                    .then(function(tmpl) {
                        scope.element.html($compile(tmpl)(scope));
                    }, function(response) {
                        if(response.status === 404) {
                            console.log('Use default template');
                            return $templateRequest('/views/reports/' + scope.flavor + '.html', true)                            
                        } else {
                            scope.element.html('Analyzer not found !');     
                        }                        
                    }).then(null, function(tmpl) {
                        scope.element.html($compile(tmpl)(scope));
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
